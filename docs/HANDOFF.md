# Hand-off for AI agents - Raibu API

Read this first, then `docs/PLAN_PRODUCTION.md` (the plan: finding IDs `F-xx`, streams `S0..S4`, per-PR acceptance criteria).
Everything in the repo (code, comments, commits, PRs, docs) is written in **English**; the owner chats in French.
Last updated: 2026-11-17 (preprod deployed on Render, see Deployment status).

## Status (2026-11-01, end of the production readiness work)
All streams are merged into `preprod` (PRs #24 to #75). `prod` was not touched: `preprod -> prod` needs the owner's explicit go.
- S0 pipeline: #24 ignore env files, #25 gate docker and deploy behind tests, #31 restore flyway, #37 remove lambda/ses/health email,
  #62 non-root Dockerfile, #68 remove terraform/ecs, #70 keepalive, #71 rollback, #72 boot-check job.
- S1 web security: #36 forwarded headers and secure cookie, #41 401 instead of redirect, #51 CSRF cookie and `/csrf`, #55 public routes and CORS,
  #56 rate limiter constructor fix, #60 banned user filter, #63 fixed 400 bodies, #66 rate limiter purge, #74 Hikari idle timeout.
- S2 identity: #27 admin role from principal, #29 `ADMIN_EMAILS`, #32 verified e-mail, #35 keep country, #38 public user profile, #44 report author and validation,
  #52 flagged queue, #57 pending reports admin only, #65 ban ends the chat, #69 report status rules.
- S3a realtime: #26 signals only in active sessions, #28 frame and rate limits, #30 `/next` re-queues, #34 disconnect grace, #39 chat length, #59 friend call signals, #61 presence listener constructor fix.
- S3b domain: #33 atomic matching, #40 endChat for both users, #45 feedback, #58 atomic reputation updates, #64 input bounds, #67 index and pagination.
- S4 docs: #42, #43, #46 to #50, #53, #54, #73, #75 (`docs/API.md`, `DEPLOYMENT.md`, `ARCHITECTURE.md`, `SECURITY.md`, README).

Deliberately NOT done:
- Testcontainers startup test against a real Postgres.
- `?before=<instant>` cursor on paginated lists (only `?limit=`, default 50, max 100).
- Shared rate limiting: both rate limiters are per instance and in memory. The REST limiter keys anonymous traffic by `getRemoteAddr()`, which is the real client IP behind the proxy because `forward-headers-strategy` is `native`.
- End-to-end STOMP test (the realtime flow is covered by unit tests only).
- Preprod is live since 2026-11-17 (see Deployment status). Prod is not deployed.

Lessons:
- Two websocket beans failed at startup because a class had two constructors and no `@Autowired` (#56, #61). Any new Spring bean with two constructors needs `@Autowired` on one.
- Gradle runs take 3 to 10 minutes on this machine: use the slot script (rule 8 of the plan), never two builds at once.

## Deployment status (2026-11-17)
Preprod is live: **https://raibu-preprod.onrender.com** (`GET /ping` returns `pong`).
- Neon project `raibu-preprod` (Postgres 16, `aws-eu-central-1`), direct (non-pooler) endpoint, database `neondb`,
  role `neondb_owner`. The host is only in the Render env and the Neon console.
- Render web service `raibu-preprod`: Free instance, Frankfurt, existing image `docker.io/tafitamathieu/raibu:preprod`,
  health check `/ping`, Docker and pre-deploy commands empty (Flyway migrates at boot).
- Render env: `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CLIENT_ID`,
  `GOOGLE_CLIENT_SECRET`, `FRONTEND_URL` and `ALLOWED_ORIGINS` (both `http://localhost:5173` until a frontend exists).
  `ADMIN_EMAILS` is not set yet.
- GitHub environment `preprod`: secret `RENDER_DEPLOY_HOOK_URL`, variable `APP_URL`. Repository secrets `DOCKERHUB_*`.
  Repository variable `PROD_URL` is not set, so the keepalive is off and preprod sleeps after 15 minutes.
- Google OAuth: redirect URI `https://raibu-preprod.onrender.com/login/oauth2/code/google` added by the owner.
- Verified: full `Build and Deploy` run green (test, build, boot-check, deploy, smoke-test); protected routes answer `401`
  without a session; Swagger/OpenAPI off; HSTS, `nosniff`, `X-Frame-Options: DENY`; `/oauth2/authorization/google`
  redirects with the `https` redirect URI.
- Not verified: a real Google sign-in followed by `GET /me`.

Next steps, in order:
1. Owner: sign in on preprod, set `ADMIN_EMAILS` in Render, add test users on the Google consent screen while in testing mode.
2. Prod, only on the owner's go: Neon `raibu-prod`, Render `raibu-prod`, Google redirect URI, GitHub `prod` environment
   (`RENDER_DEPLOY_HOOK_URL`, `APP_URL`) and repository variable `PROD_URL`, then PR `preprod -> prod`.
3. Branch protection on `preprod` and `prod` requiring CI.
4. With the frontend: real `FRONTEND_URL`/`ALLOWED_ORIGINS`; prefer API and frontend on the same site
   (`SESSION_COOKIE_SAME_SITE=lax`) since browsers block third-party cookies; add a TURN server for WebRTC behind strict NATs.
5. Open technical gaps listed under Status (Testcontainers, STOMP end-to-end test, shared rate limiting, `?before=` cursor).

Secrets handling: never print or commit DB passwords, Google secrets or the deploy hook URL; the owner pastes secrets
directly into Render and GitHub.

## What this project is
Spring Boot 3.2.2 / Java 21 backend for an Omegle-like random chat app: Google OIDC login (session cookie), matchmaking,
WebRTC signaling and text chat over STOMP/WebSocket (`/ws`), friends and DMs, reports/moderation, reputation, notifications.
Postgres via JPA; Flyway SQL in `src/main/resources/db/migration`. Package root `src/main/java/shi/raibu/shi`.
The frontend does not exist yet (separate repo, built after this backend). The project was generated by Poja (AWS Lambda);
leftovers are being removed.

## Owner decisions (do not re-ask)
- Database: Neon free. Hosting: Docker image on Render free. Prod is kept awake by pinging `/ping` (GitHub Actions cron,
  optional UptimeRobot); preprod may sleep. API domain unknown until deployment: nothing hardcoded, all env-driven.
- Admins: `ADMIN_EMAILS` env var, comma separated, set by the owner in Render.
- Moderation: no auto-ban; users with >= 3 distinct reporters in 7 days appear in an admin queue.
- Deploy: every push to `preprod`/`prod` deploys that environment after tests; `preprod -> prod` needs the owner's explicit go.
- Frontend can be changed to fit the API: the API contract is documented in `docs/API.md` (stream S4).

## Mandatory git workflow (owner's global rules, they override tool defaults)
- Default branch is `preprod`. Never commit to `preprod` or `prod`. One branch per PR from fresh `origin/preprod`.
- One commit = one coherent change, building and passing tests. Message `type: message`: one line, lowercase verb-shaped
  type (feat, fix, refactor, chore, docs, test, style, perf, build, ci, revert), no scope, no body, no "and", descriptive.
  Author `Mathieu-bot`. **No `Co-Authored-By` or AI attribution**, even if a tool default asks for it.
- Run `./format.sh` and the tests before committing. PR title = commit format, plain description, no AI footer.
  Review comment "looks good for me", then squash merge (title without `(#n)`, body = list of squashed commits),
  delete the branch remotely and locally, go back to an up-to-date `preprod`.
- Do not merge when the owner says not to. Never merge `preprod -> prod` without an explicit go.
- Each merge into `preprod`/`prod` triggers a deployment: every PR must be safe to deploy by itself.

## How to know what is done
No status table to trust. Use git: `git log --oneline origin/preprod`, `gh pr list --state merged --limit 60`, then check each
plan item's acceptance criterion in the code. Quick probes: `grep flyway build.gradle` (S0-4), `git check-ignore .env` (S0-1),
`grep -rn getSubject src/main` (S2-1), `ls src/main/java/shi/raibu/shi/mail` (S0-5), `ls docs/API.md` (S4-1).
At the end of each phase update this file (`docs: update hand-off after <phase>`): date, merged PRs, open issues.

## Verified facts (do not redo the investigation)
- Spring Boot manages Flyway 9.22.3: `flyway-core` alone supports Postgres. AOP/AspectJ are on the classpath.
- `spring-boot-starter-actuator` is not a dependency. `/ping` is the health endpoint; `/health`, `/health/live`, `/health/ready` are authenticated.
- `Principal.getName()` is the app user UUID (`AppOidcUser.getName()`), not the Google `sub`. REST and WebSocket authorization use it.
- The `ObjectMapper` ignores unknown JSON properties (clients may keep sending removed fields).
- `spring.flyway.baseline-on-migrate: true`: on a non-empty DB without history Flyway baselines at 1 and replays V2+. Inspect a real DB first.
- `server.forward-headers-strategy=framework` does not fix `getRemoteAddr()`; use `native`.
- Existing tests are unit/Mockito only. `FacadeIT` is a base class nobody extends. Gradle printed cached results before; do a fresh run.
- Remote `origin = github.com/Mathieu-bot/raibu`. Docker Hub image `tafitamathieu/raibu`. Preprod Render/Neon services exist (see Deployment status); prod ones do not.
- Local machine: 4 cores, ~7 GB RAM. Run Gradle with `--no-daemon`, one build at a time per agent.
- Untracked local files that must never be committed: `.env` (real secrets, never print values), `infrastructure/terraform/.terraform/`, `infrastructure/terraform/aws/`, `awscliv2.zip`.

## Pitfalls
- Never commit or print `.env`. Do not disable CSRF. Do not remove `@RequireRole` to make a test pass.
- `grep -rn "software.amazon\|com.amazonaws\|jakarta.mail" src/main` before removing AWS/mail dependencies.
- New migrations are additive and idempotent; never edit V1-V11. Reserved numbers: V12 (S2), V13 (S3b).
- Neon JDBC URL needs `?sslmode=require`, separate username/password, direct (non-pooler) endpoint first.
- Keep the Render-specific step isolated in the workflow so the provider can be replaced.
- If you hit a session limit mid-item, leave the work branch pushed in a building state and write the plan item ID in the PR description.
