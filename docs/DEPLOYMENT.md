# Deployment runbook (Render, Neon, GitHub Actions)

Two environments, `preprod` and `prod`, each with its own Neon database, its own Render web service and its own GitHub environment.
A push to the `preprod` or `prod` branch builds a Docker image after the tests pass and tells Render to deploy it.
Nothing in the code hardcodes a domain: every URL comes from environment variables.

Items marked **(owner action)** cannot be automated and must be done once in the provider consoles. Items marked **(planned)** are
delivered by pipeline work that was still being merged when this document was written: check `.github/workflows/` and the
`Dockerfile` before relying on them.

## 1. Environment variables

Set in the Render service (Environment tab). Names come from `src/main/resources/application.yml`, `application-prod.yml` and
`Dockerfile`.

| Variable | Required | Example / default | Purpose |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | prod: yes | `prod` | Enables `application-prod.yml`: secure session cookie, Hikari tuned for Neon, Swagger and OpenAPI disabled. |
| `DATABASE_URL` | yes | `jdbc:postgresql://ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require` | JDBC URL **without credentials**. Must start with `jdbc:postgresql://` and end with `?sslmode=require`. Default (local): `jdbc:postgresql://localhost:5432/raibu`. |
| `DATABASE_USERNAME` | yes | `neondb_owner` | Separate from the URL. Default `postgres`. |
| `DATABASE_PASSWORD` | yes | (secret) | Separate from the URL. Default `postgres`. |
| `GOOGLE_CLIENT_ID` | yes | `123-abc.apps.googleusercontent.com` | OAuth client. Empty by default, login fails without it. |
| `GOOGLE_CLIENT_SECRET` | yes | (secret) | OAuth client secret. |
| `FRONTEND_URL` | yes | `https://app.example.com` | Where the backend redirects after login (`/home`) and logout (`/login`). No trailing slash. Default `http://localhost:5173`. |
| `ALLOWED_ORIGINS` | yes | `https://app.example.com` | Comma separated exact origins allowed for CORS and WebSocket. Include the frontend origin. Default `http://localhost:5173,http://localhost:3000,https://raibu.app`. |
| `ADMIN_EMAILS` | recommended | `a@x.com,b@y.com` | Comma separated, trimmed, case-insensitive. Users signing in with these e-mails get role `ADMIN`. Empty means nobody is admin. |
| `SESSION_COOKIE_SAME_SITE` | no | `none` (default in prod) | `none` works when the frontend is on another site (requires HTTPS, already `Secure`). Use `lax` once the frontend and API share the same site. |
| `PORT` | set by Render | `10000` | Listening port (`server.port`). Default `8080`. Do not set it manually on Render. |
| `JAVA_TOOL_OPTIONS` | no | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1` | JVM flags for the 512 MB free instance. **(planned)** baked into the Dockerfile; set it in Render only to override. |
| `GOOGLE_ISSUER_URI` | no | `https://accounts.google.com` | Leave as is. |
| `API_LOCAL_URL`, `API_PRODUCTION_URL`, `CONTACT_URL` | no | see `application.yml` | Only used to fill the OpenAPI metadata (disabled in prod). |

Never put a secret in the repository. `.env` is for local use only and is git-ignored.

## 2. Neon (database)

Create **two projects** (free plan): `raibu-preprod` and `raibu-prod`.

1. Create the project, keep the default database (`neondb`) and role.
2. In the connection details select the **direct** endpoint (host without `-pooler`). Flyway and Hibernate use session features
   that the transaction pooler can break. Switch to the pooled host only if the 5 connection limit becomes a problem.
3. Build `DATABASE_URL` from the host: `jdbc:postgresql://<host>/<db>?sslmode=require`. Give username and password in
   `DATABASE_USERNAME` / `DATABASE_PASSWORD`. Do **not** paste Neon's `postgresql://user:pass@host/db` string.
4. First boot on an empty database runs the Flyway migrations `V1..V11` (and later ones) automatically; Hibernate then only validates
   (`ddl-auto: validate`).
5. Neon free suspends compute after about 5 minutes without queries and wakes up on the next connection (a few hundred ms to a
   second or two). The first request after a long idle may be slow; the Hikari pool is tuned for this
   (`minimum-idle: 0`, `max-lifetime: 240000`, `connection-timeout: 20000`, `maximum-pool-size: 5`).

## 3. Render (web service)

Create one **Web Service** per environment, instance type **Free**:

1. New, Web Service, **Existing image**: `docker.io/tafitamathieu/raibu:preprod` for preprod, `docker.io/tafitamathieu/raibu:prod`
   for prod. If the Docker Hub repository is private add a registry credential in Render.
2. Region: the one closest to the Neon project.
3. Advanced: **Health Check Path** `/ping`. **Auto-Deploy: Off** (GitHub Actions triggers deployments).
4. Add the environment variables of section 1.
5. Settings, **Deploy Hook**: copy the URL (it already contains `?key=...`). It becomes the GitHub secret `RENDER_DEPLOY_HOOK_URL`.
6. The service URL (`https://<name>.onrender.com`) is needed for the Google redirect URI (section 5) and for `APP_URL` / `PROD_URL`.

How a deploy works: the workflow calls the hook with `&imgURL=docker.io/tafitamathieu/raibu:<commit sha>`, so Render deploys that
exact immutable image, not a moving tag.

### Free-tier behavior

- The service sleeps after **15 minutes** without an inbound HTTP request or WebSocket message. The next request takes about
  **1 minute** (cold start: JVM + Flyway check) and open WebSocket connections are lost on sleep.
- All free services of a workspace share **750 instance hours per month**. One service awake 24/7 is about 744 h, so only **prod** is
  kept awake. Do not ping preprod.
- Keepalive for prod: workflow `keepalive.yml` **(planned)** calls `GET $PROD_URL/ping` every 10 minutes (curl timeout 90 s to
  tolerate a cold start) and runs only when the repository variable `PROD_URL` is set. GitHub cron can drift or be skipped under load,
  so UptimeRobot (HTTP monitor on `/ping`, 5 minutes) is an optional second pinger.
- Free tiers change often: re-check Render's and Neon's pricing pages if something behaves differently.

## 4. GitHub (Actions)

Workflows in `.github/workflows/`:

| File | Trigger | What it does |
|---|---|---|
| `ci.yml` | push on any branch except `preprod`/`prod`, PR to `prod`, `workflow_call` | Job `test` (`./gradlew test`) and job `format` (`./format.sh && git diff --exit-code`). |
| `cd.yml` | push on `preprod` / `prod`, manual | `test` (calls `ci.yml`), then `build` (Docker image pushed to Docker Hub as `:<branch>` and `:<sha>`), then `deploy` (Render hook, fails if the secret is empty), then `smoke-test` (polls `$APP_URL/ping` for up to 5 minutes; skipped with a notice when `APP_URL` is unset). One deployment per branch at a time. |
| `keepalive.yml` **(planned)** | every 10 minutes, manual | Pings production, see above. |
| rollback workflow **(planned)** | manual | See section 7. |

### GitHub environments **(owner action)**

Settings, Environments: create `preprod` and `prod` (the job uses the branch name as the environment name). For `prod`, add
**required reviewers** if you want a manual gate on every deployment.

| Name | Kind | Scope | Value |
|---|---|---|---|
| `DOCKERHUB_USERNAME` | secret | repository | Docker Hub user, `tafitamathieu` |
| `DOCKERHUB_TOKEN` | secret | repository | Docker Hub access token with write rights |
| `RENDER_DEPLOY_HOOK_URL` | secret | each environment | the hook of that environment's Render service |
| `APP_URL` | variable | each environment | `https://<render-host>` of that environment, no trailing slash, enables the smoke test |
| `PROD_URL` | variable | repository (or `prod`) | `https://<prod render host>`, enables the keepalive |

Note: the `build` job does not declare an environment, so `DOCKERHUB_*` must be **repository** secrets, not environment
secrets. `RENDER_DEPLOY_HOOK_URL` and `APP_URL` are read inside the environment of the deployed branch.

Also recommended **(owner action)**: branch protection on `preprod` and `prod` requiring the `CI` checks.

## 5. Google Cloud Console (OAuth) **(owner action)**

APIs & Services, Credentials, OAuth 2.0 Client (type Web application). Use one client per environment, or one client with several
URIs.

- Authorized redirect URIs: `https://<render-host>/login/oauth2/code/google` for **each** environment (preprod and prod hosts), plus
  `http://localhost:8080/login/oauth2/code/google` for local work.
- Authorized JavaScript origins: not required (the flow is server side).
- OAuth consent screen: add test users while the app is in testing mode.
- The backend trusts `X-Forwarded-*` headers (`server.forward-headers-strategy: native`), so behind Render the redirect URI is built
  with `https` and the public host.

## 6. Promotion preprod to prod

1. Verify `preprod`: CI and the `Build and Deploy` run are green, `GET https://<preprod-host>/ping` returns `pong`, sign in works.
2. **Only after the owner says go**: open a PR `preprod` to `prod`, review, squash merge (never merge without the owner's explicit
   approval).
3. The push on `prod` runs the same pipeline: tests, image `:prod` and `:<sha>`, Render prod deploy, smoke test on `PROD_URL`.
4. Check `/ping` and a login on prod. If it is broken, roll back (section 7).

## 7. Rollback **(planned workflow)**

A manual workflow (`workflow_dispatch`) takes two inputs, `environment` (`preprod` or `prod`) and `sha`, and calls the same Render
hook with `imgURL=docker.io/tafitamathieu/raibu:<sha>`. Every deployed commit stays available on Docker Hub as `:<sha>`, so pick the
last good commit from the Actions history. Until the workflow exists you can do it by hand: in the Render service, Manual Deploy,
Deploy an existing image, tag `<sha>`; or `curl -X POST "$RENDER_DEPLOY_HOOK_URL&imgURL=docker.io%2Ftafitamathieu%2Fraibu%3A<sha>"`.
Rolling back the image does not roll back the database: migrations are additive, so an older image still runs on a newer schema.

## 8. First deployment checklist

1. Neon: two projects, copy host, user, password.
2. Render: two free services (section 3) with all variables, health check `/ping`, auto-deploy off, copy both deploy hooks.
3. GitHub: environments `preprod` and `prod`, secrets and variables of section 4.
4. Google: redirect URIs of both hosts (section 5).
5. Push to `preprod`. Watch Actions. Open `https://<preprod-host>/ping` (wait about 1 minute if it was asleep).
6. Sign in, open `GET /me`, check Render logs for `Successfully applied N migrations`.

## 9. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Google error `redirect_uri_mismatch` | The URI Google received is not in the client's list. Check that `https://<render-host>/login/oauth2/code/google` is registered exactly (scheme `https`, no trailing slash). If the shown URI starts with `http://`, the proxy headers are not honored: check that `server.forward-headers-strategy` is `native` and that you are using the Render URL, not a custom proxy. |
| `403` on every `POST`/`PUT`/`DELETE` and on `/logout` | Missing CSRF token (`{"code":"CSRF_INVALID"}`): fetch it from `GET /csrf` and send it in the `X-XSRF-TOKEN` header (see `docs/API.md`), with `credentials: 'include'`. |
| `401` on every API call from the frontend | The session cookie is not sent: use `credentials: 'include'`, make sure `ALLOWED_ORIGINS` contains the exact frontend origin, and that the cookie is `SameSite=None; Secure` (cross-site) or `lax` (same site, `SESSION_COOKIE_SAME_SITE=lax`). Browsers that block third-party cookies need the API and frontend on the same site. |
| CORS error in the browser | `ALLOWED_ORIGINS` missing the origin, or has a trailing slash or a different scheme/port. |
| `Found non-empty schema(s) "public" but no schema history table` / strange baseline | The database already had tables. `baseline-on-migrate: true` baselines at version 1 and replays V2 and later, which fails or duplicates if the schema is already further along. Use a fresh Neon project or database, or inspect the real schema first and baseline at the matching version (`flyway_schema_history`). |
| `Schema-validation: missing table` | Flyway did not run (check logs) or ran against another database/schema. |
| Startup fails with a connection or SSL error | `DATABASE_URL` must start with `jdbc:postgresql://` and contain `?sslmode=require`; credentials only in `DATABASE_USERNAME`/`DATABASE_PASSWORD`; use the direct Neon host. |
| First request after idle takes 30-60 s or times out | Render cold start (service asleep). Normal on free preprod; prod is kept awake by the keepalive. Retry. |
| First DB query slow or one failed request after idle | Neon compute was suspended and woke up. The pool recovers on retry (`connection-timeout` 20 s). |
| Deploy job fails with `RENDER_DEPLOY_HOOK_URL is not set` | The secret is missing in the GitHub environment named after the branch. |
| Smoke test stays red | `APP_URL` is wrong, or the service is still starting (it waits up to 5 minutes). Check the Render logs. |
| `denied: requested access to the resource is denied` in the build job | `DOCKERHUB_USERNAME` / `DOCKERHUB_TOKEN` missing or the token has no write rights. |
| Admin endpoints return `403` | The e-mail of the signed-in Google account is not in `ADMIN_EMAILS`, or the variable was changed without restarting the service. |
| `/swagger-ui` returns `404`/`401` in prod | Intentional: Swagger and OpenAPI are disabled with profile `prod`. |

## 10. Local run (summary)

See `README.md`. Short version: `docker compose up -d db`, export `DATABASE_URL=jdbc:postgresql://localhost:55432/raibu`
`DATABASE_USERNAME=raibu_admin` `DATABASE_PASSWORD=raibu_password` `GOOGLE_CLIENT_ID` `GOOGLE_CLIENT_SECRET`, then `./gradlew bootRun`.
