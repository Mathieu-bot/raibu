# Raibu API

Backend of Raibu, an Omegle-like random chat: Google sign-in, matchmaking, WebRTC signaling and text chat over STOMP/WebSocket,
friends and direct messages, reports and moderation, reputation and notifications.

Stack: Java 21, Spring Boot 3.2.2 (Web, Security with OAuth2 client, WebSocket/STOMP, Data JPA), PostgreSQL, Flyway, springdoc
(Swagger UI, disabled in production). Package root: `src/main/java/shi/raibu/shi`.

## Documentation

| Document | Content |
|---|---|
| [`docs/API.md`](docs/API.md) | REST and WebSocket contract for the frontend (start here if you build the client) |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Components, matchmaking flow, locking, state machines, data model |
| [`docs/SECURITY.md`](docs/SECURITY.md) | Roles, admins, moderation, bans, rate limits, CSRF |
| [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md) | Render, Neon and GitHub environments, environment variables, rollback, troubleshooting |

## Requirements

- Java 21
- Docker (for the local PostgreSQL)
- A Google OAuth client (Web application) for sign-in: see below

## Run locally

### 1. Start PostgreSQL

```bash
docker compose up -d db
```

PostgreSQL listens on `localhost:55432`, database `raibu`, user `raibu_admin`, password `raibu_password`.

### 2. Create a Google OAuth client

Google Cloud Console, APIs & Services, Credentials, OAuth client ID, type Web application. Add the authorized redirect URI
`http://localhost:8080/login/oauth2/code/google`. Copy the client id and secret.

### 3. Configure environment variables

The app reads everything from the environment (`src/main/resources/application.yml`). You can put them in a git-ignored `.env`
(see `.env.example`) and run `./run-local.sh`, or export them:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:55432/raibu'
export DATABASE_USERNAME='raibu_admin'
export DATABASE_PASSWORD='raibu_password'
export GOOGLE_CLIENT_ID='...'
export GOOGLE_CLIENT_SECRET='...'
export FRONTEND_URL='http://localhost:5173'
export ALLOWED_ORIGINS='http://localhost:5173,http://localhost:3000'
export ADMIN_EMAILS='you@example.com'   # optional: makes this Google account an admin
```

Without `DATABASE_*` the defaults are `jdbc:postgresql://localhost:5432/raibu`, `postgres` / `postgres`, which do not match the
compose database. The full variable list is in [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md#1-environment-variables).

### 4. Start the backend

```bash
./gradlew bootRun
```

Flyway creates the schema on first start (`src/main/resources/db/migration`). Then:

- REST: `http://localhost:8080` (health: `curl http://localhost:8080/ping` returns `pong`)
- WebSocket (STOMP over SockJS): `http://localhost:8080/ws`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html` (needs a signed-in session; disabled with `SPRING_PROFILES_ACTIVE=prod`)
- Sign in: open `http://localhost:8080/oauth2/authorization/google` in a browser

### Docker

`docker compose up --build` starts PostgreSQL and the backend container on port 8080. The `app` service in `docker-compose.yml` does not
define `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET`: add them to its `environment` section to sign in.

## Build, test, format

```bash
./gradlew test        # unit tests (Mockito, no database needed)
./gradlew bootJar     # build/libs/app.jar
./format.sh           # google-java-format, required by CI
```

CI (`.github/workflows/ci.yml`) runs the tests and checks that `./format.sh` leaves no diff. Every push to `preprod` or `prod` builds the
Docker image `tafitamathieu/raibu` and deploys to Render, see [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md).

## Project layout

```
src/main/java/shi/raibu/shi
  endpoint/      REST controllers, DTOs, rate limiting and request logging
  websocket/     STOMP controller, config, presence listener, message models
  service/       matchmaking, feedback, friendship, direct messages, notifications, reputation
  security/      Spring Security config, Google user mapping, roles
  model/ repository/  JPA entities and Spring Data repositories
  exception/     global REST exception handler
src/main/resources/db/migration   Flyway SQL migrations (never edit an applied one, add a new file)
```

## Troubleshooting

- `password authentication failed for user "postgres"`: you are using the default credentials. Export the `DATABASE_*` variables of
  step 3 (the compose database is on port `55432`).
- `redirect_uri_mismatch`: the redirect URI is not registered in the Google client, see step 2.
- Browser requests fail with CORS errors: add the frontend origin to `ALLOWED_ORIGINS`.
- More cases (Render, Neon, Flyway): [`docs/DEPLOYMENT.md`](docs/DEPLOYMENT.md#9-troubleshooting).
