# Security and moderation guide

How access control, moderation and abuse protection work in the backend, and what is deliberately not done. For the client-facing
contract (status codes, payloads) see `docs/API.md`. Items tagged **in flight** are decided and being merged by other work streams: check
the code or `gh pr list --state merged` for their state.

## 1. Authentication

- Google OIDC only. No local passwords, no tokens. A server-side session (`JSESSIONID` cookie) identifies the user.
- At sign-in `CustomOidcUserService` refuses Google accounts whose e-mail is missing or **not verified** (OAuth error, no session).
- The application user id (UUID) is the security principal name. It is never the Google `sub`, so ids exposed to other users carry
  no Google identifier.
- Unauthenticated REST calls get `401 {"code":"UNAUTHENTICATED"}`. Only an explicit browser navigation is redirected to Google.
- Session cookie in production (`SPRING_PROFILES_ACTIVE=prod`): `Secure`, `SameSite=None` by default (`SESSION_COOKIE_SAME_SITE` to change).
  `server.forward-headers-strategy: native` makes the app see the real scheme and client IP behind Render's proxy.
- Sessions are in memory: a restart signs everybody out.

## 2. Roles and admins

| Role | How it is obtained |
|---|---|
| `USER` | every signed-in account |
| `ADMIN` | the account's e-mail is in the `ADMIN_EMAILS` environment variable |
| `MODERATOR` | defined in the enum and in the hierarchy (`ADMIN` > `MODERATOR` > `USER`) but **never assigned**: no code grants it |

- `ADMIN_EMAILS`: comma separated list, entries trimmed and compared case-insensitively. Empty or unset means nobody is admin. Changing the value needs a
  service restart. The role is computed on each protected call from the stored e-mail (no role column).
- Because admin status comes from the e-mail, the verified-e-mail check at sign-in is what stops someone from claiming an admin address.
- Protected methods carry `@RequireRole(Role.ADMIN)`, enforced by `SecurityAspect` using the authenticated principal id. Failure: `403`.
- Never remove `@RequireRole` to make a test pass.

Admin endpoints (all `ADMIN`): `GET /admin/reports`, `GET /admin/users/{id}/reports`, `GET /admin/users/{id}/sessions`,
`PUT /admin/reports/{id}/status`, `PUT /admin/users/{id}/ban`. `GET /admin/reports/flagged` is the moderation queue (section 3); `GET /reports/pending` is covered
in section 3 (**in flight**).

## 3. Reports and moderation

There is **no automatic ban**. Moderation is human.

1. A signed-in user reports another user: `POST /reports` with `reportedUserId`, `reason` (`INAPPROPRIATE_BEHAVIOR`, `NUDITY`, `HARASSMENT`, `SPAM`, `OTHER`)
   and optionally `sessionId`. The author is always the authenticated user (a `reporterId` in the body is ignored). Rules: the target must exist and not be the reporter;
   a `sessionId` must exist and include both users; one report per (reporter, target, session), the second gives `409`. A database unique index backs the last rule.
   Status `PENDING`.
2. **Moderation queue**: `GET /admin/reports/flagged?minReporters=3&days=7` lists users reported by at least 3 **distinct** reporters in the last 7 days, with
   `distinctReporters`, `reportCount`, `lastReportAt`. Counting distinct reporters stops one person from flagging someone by spamming.
3. An admin reviews reports (`GET /admin/reports?status=PENDING`, `GET /admin/users/{id}/reports`, `GET /admin/users/{id}/sessions`). Chat history
   (`GET /sessions/{id}/messages`) is limited to the two participants, so admins judge from the report data and the session list.
4. `PUT /admin/reports/{id}/status` with `REVIEWED` or `ACTIONED`. Transition to `ACTIONED` adds a strike (`strike_count + 1`), -20 reputation, and notifies the reporter
   (`REPORT_ACTIONED`). Repeating the same status must not repeat side effects (**in flight**).
5. `PUT /admin/users/{id}/ban` with `{"banned": true|false}`. Banning sets `banned`, caps reputation at -50 and notifies the user (`USER_BANNED`); unbanning notifies
   (`USER_UNBANNED`). Ending the banned user's current chat on ban is **in flight**.

### What a ban does

- STOMP: search, signal, chat, DM and next are refused with `ERROR USER_BANNED` on `/user/queue/match`.
- Matching: banned users are excluded from candidates and a claim re-checks the ban under lock.
- REST: today the ban is not enforced on REST. **In flight**: a filter returns `403 {"code":"USER_BANNED"}` on every REST call except `GET /me`, `/logout`, `/ping`,
  `/login/**`, `/oauth2/**`, with one primary-key lookup per request and no cache so a ban is immediate.
- The ban flag is not cleared by logging in again: it lives on the user row.

### Reputation

Score in [-100, 100], starts at 0: +1 when the peer likes the session, -3 on a dislike, -20 per actioned report, at most -50 on ban. Users with a score below -49
are not matched at all. Feedback is accepted once per user per session and only after the session ended.

## 4. Rate limits and size limits

| Where | Limit | On exceed |
|---|---|---|
| REST writes (`POST`/`PUT`/`DELETE`) on `/reports`, `/me`, `/users` | 30 per minute per user (per IP when anonymous) | `429` |
| Other REST requests | 300 per minute per user (per IP when anonymous) | `429` |
| STOMP SEND frames | 100 per 10 s per user | frame dropped, `ERROR RATE_LIMITED` |
| STOMP message size | 64 KB | frame rejected by the transport |
| Chat message | 2000 characters | `ERROR MESSAGE_TOO_LONG` |
| Report description | 1000 characters | `400` |

Limits are fixed windows kept in memory, **per instance**; they reset on restart. The REST limiter keys anonymous traffic by `getRemoteAddr()`, which is the
real client IP because forwarded headers are honored. REST limiter memory is bounded by a scheduled purge (**in flight**).

## 5. CSRF and CORS

- CSRF protection is on for all state-changing REST calls (and `/logout`). It is never disabled. `/ws/**` is exempt because browsers cannot attach custom headers to the
  SockJS handshake and the WebSocket origin is checked against `ALLOWED_ORIGINS`.
- Double-submit cookie: the token is written in a readable `XSRF-TOKEN` cookie and expected back in the `X-XSRF-TOKEN` header. A cross-site frontend fetches the value from
  `GET /csrf` because it cannot read the API's cookie (see `docs/API.md`). Failures return `403 {"code":"CSRF_INVALID"}`.
- CORS: only the origins in `ALLOWED_ORIGINS` (exact match), credentials allowed, methods `GET POST PUT DELETE OPTIONS`. The same list is the allowed-origin list of the WebSocket
  endpoint. A wrong value shows up as CORS errors or a failing handshake.

## 6. Realtime protections

- `/app/signal` relays only between two users who share an `ACTIVE` chat session; otherwise `ERROR NO_ACTIVE_SESSION`. The `from` field is overwritten with the real sender.
- `/app/chat` requires an active session with the recipient; `/app/dm` requires a friendship (`ERROR NOT_FRIENDS`).
- A user is in at most one active session; claims are done under ordered pessimistic locks (see `docs/ARCHITECTURE.md`).
- Disconnect handling releases users after a 10 s grace period so no ghost `SEARCHING`/`IN_CHAT` user stays in the pool.

## 7. Data exposure

- `GET /users/{id}` returns only `id`, `displayName`, `avatarUrl`, `countryCode`. E-mail, provider ids, ban and reputation data are never returned for other users.
  `GET /me` returns the caller's own e-mail and `banned`.
- `POST /users/register` and `PUT /users/{id}/session` were removed.
- Session history and messages are restricted to the participants; DMs to the two friends; notifications to their owner.
- Error responses: validation and service errors return a short message; unexpected errors return a generic 500 body. Stack traces are logged, not returned
  (raw exception messages still reach clients for some `400`s, **in flight** to be replaced by fixed messages).
- Swagger UI and `/v3/api-docs` are disabled with profile `prod` and need a session otherwise.
- Secrets (`GOOGLE_CLIENT_SECRET`, `DATABASE_PASSWORD`) come from environment variables only. `.env` is git-ignored.

## 8. Deliberately not done

- **No automatic ban or shadow ban.** Humans decide.
- **No `MODERATOR` assignment**: only `ADMIN` exists in practice.
- **No content scanning**: text and video are not analyzed. Video is peer to peer (WebRTC), so the server never sees it. Chat texts are stored.
- **No age verification, no blocking list**, no IP ban. A banned user can only be re-admitted by an admin; a new Google account bypasses a ban.
- **No distributed rate limiting or shared sessions**: the app is a single instance by design (free Render plan).
- **No audit log** of admin actions (only application logs).
- **No TURN/STUN service**: peers behind strict NATs may fail to connect; the frontend chooses the ICE servers.
- **No reporting of DMs**: reports target a user and optionally a random-chat session.
- CSRF is not disabled and `@RequireRole` is not bypassed for convenience.

## 9. Operational checklist

1. Set `ADMIN_EMAILS` in Render for each environment, restart.
2. Keep `ALLOWED_ORIGINS` limited to the real frontend origins.
3. Use `SPRING_PROFILES_ACTIVE=prod` in both environments (secure cookie, no Swagger).
4. Rotate the Google client secret and the Neon password if they were ever printed or committed.
