# Raibu - API

Spring Boot api for Raibu (matchmaking + WebRTC signaling + chat + moderation + notifications).

## Requirements

- Java 21
- Docker (optional, for PostgreSQL)

## Quick start (local)

### 1) Start PostgreSQL

From this folder:

```bash
docker compose up -d db
```

This starts PostgreSQL on:

- Host: `localhost`
- Port: `55432`
- Database: `raibu`
- User: `raibu_admin`
- Password: `raibu_password`

### 2) Configure environment variables

The backend reads configuration from environment variables (see `src/main/resources/application.yml` and `docker-compose.yml`).

Typical local setup:

```bash
export DATABASE_URL='jdbc:postgresql://localhost:55432/raibu'
export DATABASE_USERNAME='raibu_admin'
export DATABASE_PASSWORD='raibu_password'
export PORT='8080'
export ALLOWED_ORIGINS='http://localhost:5173,http://localhost:8080'

# Google OAuth (required for authenticated endpoints)
export GOOGLE_CLIENT_ID='...'
export GOOGLE_CLIENT_SECRET='...'
```

Important:

- If you do not set `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD`, the defaults from `application.yml` are used:
  - `jdbc:postgresql://localhost:5432/raibu`
  - user `postgres`
  - password `postgres`
- If you started the DB using `docker compose up -d db`, you MUST use the docker-compose credentials/port (`55432`, `raibu_admin`, `raibu_password`) via env vars as shown above.

Notes:

- If your Vite dev server uses another port (for example `5175`), add it to `ALLOWED_ORIGINS`.

### 3) Run the backend

```bash
./gradlew bootRun
```

Backend will be available at:

- REST: `http://localhost:8080`
- WebSocket STOMP (SockJS): `http://localhost:8080/ws`

## Quick start (Docker)

Run PostgreSQL + backend together:

```bash
docker compose up --build
```

Exposed ports:

- Backend: `http://localhost:8080`
- Postgres: `localhost:55432`

## Healthcheck

```bash
curl http://localhost:8080/ping
```

Expected output:

```text
pong
```

## Troubleshooting

### `FATAL: password authentication failed for user "postgres"`

This means the backend is trying to connect with the default DB user (`postgres`). Fix with one of these:

- Start the provided Docker DB and export the env vars:

```bash
docker compose up -d db
export DATABASE_URL='jdbc:postgresql://localhost:55432/raibu'
export DATABASE_USERNAME='raibu_admin'
export DATABASE_PASSWORD='raibu_password'
./gradlew bootRun
```

- Or run a local Postgres that matches the defaults (`localhost:5432`, `postgres`/`postgres`).

## Main features

- Google OAuth login (Spring Security OAuth2 client)
- Matchmaking over WebSocket/STOMP:
  - Send: `/app/search`, `/app/next`, `/app/stop`, `/app/signal`, `/app/chat`, `/app/dm`
  - Subscribe: `/user/queue/match`, `/user/queue/signal`, `/user/queue/chat`, `/user/queue/dm`, `/user/queue/notifications`
- Persistence (PostgreSQL + Flyway migrations)

## Docs

- Backend API documentation is available in:
  - `docs/backend-api.md`
