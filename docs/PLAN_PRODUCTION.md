# Production readiness plan — Raibu API

> **Status (2026-11-01): the plan was executed.** Done: every finding F-01 to F-32 and streams S0 to S4 (PRs #24 to #75, listed in `docs/HANDOFF.md`).
> Not done: Testcontainers startup test (S0-9), `?before=` pagination cursor, a live Render/Neon deployment (owner actions in `docs/DEPLOYMENT.md`),
> the `preprod -> prod` promotion. The rest of this document is kept as history; where it disagrees with the code or `docs/API.md`, the code wins.

Written 2026-11-01 from a full code review (4 passes). `file:line` references come from the reviews:
**re-read the code before fixing, lines move.** Language rule: code, comments, commits, PRs and docs are in English.

## 0. Decisions (owner, final)

| Topic | Decision |
|---|---|
| Frontend | Not built yet, separate repo, built after the backend. API/DTOs may change freely; the REST + WebSocket contract is documented (S4). |
| Product | Omegle-like random chat: matchmaking, WebRTC signaling, text chat, friends/DMs. |
| Database | Neon (free). |
| Hosting | Docker container on Render **free** for prod, kept awake by a ping on `/ping`. preprod is also on free Render and may sleep (no ping). |
| API domain | Unknown until deployment, so nothing may hardcode a domain: everything is env-driven. |
| Moderation | No automatic ban. Users reported by >= 3 distinct reporters (7 days) appear in an admin queue. |
| Admins | `ADMIN_EMAILS` env var, comma separated (`a@x.com,b@y.com`), trimmed, case-insensitive. Empty = nobody is admin. The owner sets the value in Render. |
| Deploy | Push to `preprod` deploys preprod, push to `prod` deploys prod, only after tests pass. Feature branches: CI only. `preprod -> prod` promotion needs the owner's explicit go. |
| Deadline | Fixes must land in ~1-2 h; frontend team is waiting. Everything merged must work: no half-done items. |

Hosting facts (checked 2026-11-01): free Render sleeps after 15 min without inbound HTTP request or WebSocket message
(~1 min restart), 750 free instance hours/month shared by all free services in the workspace (one always-on service = ~744 h,
so preprod must not be pinged). Free Render Postgres expires after 30 days: not used. Neon free suspends compute after
5 min idle. Free tiers change often: re-check the providers' pages if something behaves differently.

## 1. Findings

Priority: **B** blocks production, **S** security, **L** business logic, **D** debt. Stream = who fixes it (section 3).

| ID | P | Finding | Where | Stream |
|---|---|---|---|---|
| F-01 | B | Flyway missing from `build.gradle` (removed by poja commit `651f8c1`, revert `ee4a011` did not restore it): V1-V11 never run, `ddl-auto: validate` fails on an empty DB | `build.gradle` | S0 |
| F-02 | B | No `server.forward-headers-strategy`: behind Render the OAuth redirect URI is `http://` -> Google `redirect_uri_mismatch`; session cookie not Secure | `application.yml` | S1 |
| F-03 | B | CSRF defaults: a cross-origin SPA gets 403 on every POST/PUT/DELETE and `/logout` | `SecurityConfig` | S1 |
| F-04 | B | Unauthenticated API call -> 302 to Google instead of 401; same for `/ws/**` | `SecurityConfig` | S1 |
| F-05 | B | CD deploys without waiting for tests; missing deploy-hook secret exits 0 silently | `cd.yml` | S0 |
| F-06 | S | `createReport` trusts `reporterId` from the body; auto-ban counts rows not reporters; no existence/self/session checks | `ReportController` | S2 |
| F-07 | S | `SecurityAspect` uses `getSubject()` (Google sub) but app ids are UUIDs (`AppOidcUser.getName()`): `/admin/**` always 403 | `SecurityAspect` | S2 |
| F-08 | S | Admins hardcoded by e-mail, `email_verified` unchecked | `RoleService`, `CustomOidcUserService` | S2 |
| F-09 | S | `/signal` relays to any recipient without a shared ACTIVE session; `/ws/**` has no rate limit or size limit | `SignalingController`, `WebSocketConfig` | S3a |
| F-10 | S | `GET /health/email?to=` sends 5 e-mails to any address for any logged-in user | `HealthEmailController` | S0 |
| F-11 | S | `GET /users/{id}` returns the full `User` entity (e-mail, providerId...); `PUT /users/{id}/session` has no owner check; `POST /users/register` creates blank rows | `UserController` | S2 |
| F-12 | S | `GET /reports/pending` has no `@RequireRole` | `ReportController` | S2 |
| F-13 | S | Banned users can still use REST (DM, feedback, ...); a ban does not end the current chat | `FriendsController`, `SessionController`, `AdminModerationController` | S1 (filter), S2 (end chat on ban) |
| F-14 | S | `PUT /admin/reports/{id}/status` accepts null and is not idempotent (strikes/notifications repeat) | `AdminModerationController` | S2 |
| F-15 | S | `GlobalExceptionHandler` returns raw IAE/ISE messages | `GlobalExceptionHandler` | S1 |
| F-16 | S | Rate limiter keys anonymous traffic by proxy IP, unbounded `HashMap` | `RateLimitingConfigurer`, `RateLimiterService` | S1 |
| F-17 | S | Swagger visible to any logged-in user; `/actuator/**` permitted but actuator does not exist; duplicate CORS config (`CorsConfigurer` is dead) | `SecurityConfig`, `CorsConfigurer` | S1 |
| F-18 | S | No size limits: chat/DM text, preference lists (varchar(255) overflow -> 500) | `SignalingController`, `UserPreferencesDto`, `DirectMessageService` | S3a (chat), S3b (rest) |
| F-19 | L | Matchmaking has no locking: a user can land in two sessions | `MatchmakingService` | S3b |
| F-20 | L | `/next` does not re-queue the user | `SignalingController` | S3a |
| F-21 | L | `endChat` only releases the caller; peer not released/notified; `PEER_DISCONNECTED` never emitted | `MatchmakingService` | S3b |
| F-22 | L | No WebSocket disconnect handling: ghost SEARCHING/IN_CHAT users, endless ACTIVE sessions | (missing) | S3a |
| F-23 | L | Several ACTIVE sessions for one pair -> `IncorrectResultSizeDataAccessException` in `/chat` | `MatchmakingService` | S3b |
| F-24 | L | Reputation <= -50 filter bypassed by the fallback random pick | `MatchmakingService` | S3b |
| F-25 | L | Every login overwrites `countryCode` (Google locale or null) | `CustomOidcUserService` | S2 |
| F-26 | L | `FeedbackService` not transactional, feedback accepted on ACTIVE sessions (reputation farming), double submit -> 500, simultaneous mutual like missed | `FeedbackService`, `SessionController` | S3b |
| F-27 | L | Lost updates on `User` (reputation counters, preferences vs ban) | `ReputationService`, `MeController` | S3b |
| F-28 | L | Missing index `chat_session(user2_id)`; unbounded lists (`findAll`, histories, `/friends`) | migrations, controllers | S3b |
| F-29 | D | Poja/AWS leftovers: `LambdaHandler`, `mail` package (SES, `noreply@poja.io`), `aws-*` deps, `infrastructure/`, `.shell/`, `release-version.yml`, test confs `BucketConf`/`EmailConf` | various | S0 |
| F-30 | D | Dockerfile: root, no JVM flags, no dependency layer cache, HEALTHCHECK hardcoded to 8080 | `Dockerfile` | S0 |
| F-31 | D | CI: `branches: '*'` misses `feat/x`; no controller/security/context tests; jacoco minimum 0 | `ci.yml`, `build.gradle` | S0, all |
| F-32 | D | `.gitignore` misses `.env` and terraform dirs (nothing was ever committed: verified on all refs) | `.gitignore` | S0 |

Withdrawn after verification: "actuator exposed" (no actuator dependency); "tests need Docker" (they are unit tests).

## 2. Rules for every agent (read fully)

1. **Git (owner's rules, they override tool defaults).** One branch per PR, created from fresh `origin/preprod`
   (`git fetch origin && git switch -c <type>/<slug> origin/preprod`). Never commit to `preprod`/`prod`.
   One commit per PR (one coherent change, building and passing tests). Message: `type: message`, one line, lowercase verb-shaped
   type (feat, fix, refactor, chore, docs, test, style, perf, build, ci, revert), **no scope, no body, no "and"**, descriptive
   (what and why), vary the leading verb. Author is already `Mathieu-bot`. **No `Co-Authored-By`, no AI attribution anywhere.**
2. **Before committing:** `./format.sh` (google-java-format, CI fails otherwise; commit the result), then the test command from
   section 5. Do not commit on failure. Every fix ships tests that fail without the fix.
3. **PR and merge, right after opening (no waiting for CI):**
   `gh pr create --base preprod --title "<same as commit>" --body "<2-4 plain sentences>"` (no AI footer), then
   `gh pr review <n> --comment --body "looks good for me"`, then
   `gh pr merge <n> --squash --delete-branch --subject "<PR title>" --body "- <commit subject>"` (no `(#n)` in the subject; the body
   lists the squashed commits). Then `git fetch origin && git switch --detach origin/preprod` (in a worktree `preprod` is checked out
   elsewhere) and delete the local branch with `git branch -D <branch>`.
4. **File ownership.** Only edit files owned by your stream (section 3). If you truly need another stream's file, make the smallest
   possible change in its own PR, `git fetch && git rebase origin/preprod`, re-run tests, then merge. Never reformat unrelated code.
5. **Keep these signatures stable** (other streams call them):
   `MatchmakingService.endChat(String userId)`, `.startSearching(String userId, String mode)`, `.findRandomMatch(String userId): Optional<User>`,
   `.isUserBanned(String userId)`, `.stopSearching(String userId)`; `RoleService.hasRole/isAdmin(String userId, ...)`.
6. **Migrations.** Flyway is not on yet in `preprod`'s history, so new files only: S2 -> `V12__*.sql`, S3b -> `V13__*.sql`, S0 never adds one.
   Additive and idempotent (`IF NOT EXISTS`). Never edit V1-V11.
7. **Do not touch** `.env` (real secrets, never print its values) or any untracked file. Never print secrets.
8. **Builds share a small machine (4 cores, ~7 GB).** Always run Gradle through the slot script:
   `SLOT=/tmp/claude-1000/-home-jiu-RAIBU-raibu/9bfee2d3-eafb-4fc4-8c49-51b3d11893fc/scratchpad/gradle-slot.sh; $SLOT test`
   (it runs `./gradlew --no-daemon` under a 2-slot lock). Never run two Gradle commands at once yourself.
9. **Time box.** Run `date` at start. Do not start a new PR after 55 minutes. Items are ordered by priority: if you run out of time,
   stop cleanly (nothing half-merged) and list what remains in your final report.
10. **Final report** (<= 250 words): merged PRs (number + title), skipped items and why, new env vars, anything the frontend must know
    (new/removed endpoints, changed payloads, new error codes/message types), anything you could not verify.

## 3. Streams (parallel, disjoint files)

Order inside a stream = order of PRs. Acceptance = how to prove it is done.

### S0 - Pipeline, build and cleanup (agent `pipeline`)
Owns: `.gitignore`, `.dockerignore`, `Dockerfile`, `docker-compose.yml`, `build.gradle`, `.github/**`, `infrastructure/`, `.shell/`,
`src/main/java/shi/raibu/shi/{handler,mail}/**`, `endpoint/rest/controller/health/HealthEmailController.java`, `src/test/**/conf/**`, `docs/PLAN_PRODUCTION.md`, `docs/HANDOFF.md`.
1. `chore: ignore env files, terraform state and archives` (F-32). Add `.env`, `.env.*` (keep `!.env.example`), `infrastructure/terraform/.terraform/`, `*.tfstate*`, `*.zip`; `.dockerignore`: `*.jar` except wrapper, `*.zip`. Acceptance: `git check-ignore .env` exits 0 and `git status` no longer lists the untracked files.
2. `ci: gate docker publish and render deploy behind tests` (F-05, F-31). `ci.yml`: `workflow_call`, branches `'**'`, keep test + format jobs. `cd.yml`: jobs `test` (uses ci.yml) -> `build` (cache `type=gha`, tags `:<sha>` and `:<branch>`) -> `deploy` (environment = branch name; **fail** if `RENDER_DEPLOY_HOOK_URL` is empty; deploy by image SHA) -> smoke test (loop `GET ${{ vars.APP_URL }}/ping` until 200, 5 min timeout; if `vars.APP_URL` is unset print a notice and skip, the domain is not known yet). `concurrency: group: deploy-${{ github.ref_name }}, cancel-in-progress: false`. Acceptance: workflow YAML valid (`actionlint` if available, else careful review) and `needs:` chain shows tests before build.
3. `ci: ping the production service to keep the free instance awake`. New `keepalive.yml`: `schedule: */10 * * * *` + `workflow_dispatch`, runs only when `vars.PROD_URL` is set, `curl --fail --max-time 90 "$PROD_URL/ping"` (90 s tolerates a cold start). Document that GitHub cron can drift and that UptimeRobot (5 min) is an optional second pinger; preprod is never pinged.
4. `fix: restore flyway so migrations run at startup` (F-01). `implementation 'org.flywaydb:flyway-core'` (Boot 3.2.2 manages Flyway 9.22.3; no `flyway-database-postgresql`). Acceptance: app starts against an **empty** Postgres (`docker compose up -d db` then `./gradlew bootRun` with the compose env, or Testcontainers) and `ddl-auto: validate` passes.
5. `refactor: remove poja lambda handler, ses mailer and health email endpoint` (F-10, F-29 code). First `grep -rn "software.amazon\|com.amazonaws\|jakarta.mail\|shi.raibu.shi.mail" src`; delete `LambdaHandler`, `mail/**`, `HealthEmailController`, the `aws-*`/`awssdk`/`jakarta.mail`/`activation` dependencies and test confs that only serve them. Keep `PojaGenerated`/`PojaApplication` only if still referenced (rename is out of scope). Acceptance: `./gradlew test` green, `/health/email` is 404, `tika-core`/`reflections` kept only if used (grep).
6. `chore: remove terraform, ecs scripts and unused release workflow` (F-29 files): `infrastructure/`, `.shell/`, `.github/workflows/release-version.yml`, `format.bat` only if unused. Do not delete untracked local files.
7. `build: run the container as non root with jvm memory limits and a cached dependency layer` (F-30). Multi-stage: copy `gradlew gradle build.gradle settings.gradle lombok.config`, resolve dependencies in their own layer, then `COPY src` and `./gradlew bootJar --no-daemon -x test`. Runtime: non-root user, `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"`, `HEALTHCHECK CMD wget -qO- http://localhost:${PORT:-8080}/ping` (shell form). Acceptance: `docker build` then `docker run` with a Postgres env starts and `curl localhost:$PORT/ping` returns 200 (run it).
8. `ci: add a manual rollback workflow that redeploys a previous image` : `workflow_dispatch` with `environment` + `sha` inputs calling the same Render hook with that image.
9. `test: verify flyway migrations and schema validation against a real postgres` (F-31). Spring Boot test with Testcontainers Postgres (`@Testcontainers(disabledWithoutDocker = true)`), asserting the context starts with Flyway + `validate`. Needs `org.testcontainers:postgresql` (version aligned with 1.19.1). Needs a Google client registration stub via test properties. Rebase on origin/preprod first (S1 changes `application*.yml`).

### S1 - Web security configuration (agent `websec`)
Owns: `security/SecurityConfig.java`, new `security/*Filter*.java`, `src/main/resources/application*.yml`, `endpoint/CorsConfigurer.java` (delete), `endpoint/RateLimitingConfigurer.java`, `service/RateLimiterService.java`, `exception/**`, `config/**`, and new tests for them.
1. `fix: honor proxy forwarded headers and secure session cookies in prod` (F-02). `server.forward-headers-strategy: native` (**not** `framework`: only `native` fixes `getRemoteAddr()`, needed by F-16). New `application-prod.yml`: `server.servlet.session.cookie.secure: true`, `same-site: ${SESSION_COOKIE_SAME_SITE:none}` (None + Secure works same-site and cross-site over HTTPS; the owner can set `lax` once the front shares the domain), `springdoc.api-docs.enabled: false`, `springdoc.swagger-ui.enabled: false`, Hikari for Neon (`maximum-pool-size: 5`, `minimum-idle: 0`, `max-lifetime: 240000`, `connection-timeout: 20000`). Document that `DATABASE_URL` must be `jdbc:postgresql://<host>/<db>?sslmode=require` with separate username/password. Acceptance: test that a request with `X-Forwarded-Proto: https` builds an `https` redirect URI to `/oauth2/authorization/google`.
2. `fix: return 401 instead of redirecting unauthenticated api calls to google` (F-04). `defaultAuthenticationEntryPointFor(HttpStatusEntryPoint(UNAUTHORIZED), ...)` for `/ws/**` and for requests with `Accept: application/json` or `X-Requested-With: XMLHttpRequest`; browser navigation (`/oauth2/authorization/google`) still redirects. Acceptance: MockMvc: JSON request -> 401, HTML navigation -> 302.
3. `fix: expose the csrf token in a readable cookie for the single page app` (F-03). `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `CsrfTokenRequestAttributeHandler`; keep `/ws/**` ignored; make sure the cookie is issued on a plain GET (e.g. `/me`). Acceptance: MockMvc POST without token -> 403, with `X-XSRF-TOKEN` -> not 403.
4. `chore: tighten public routes and drop the duplicate cors configuration` (F-17). `permitAll` only `/ping` (and OAuth login endpoints handled by `oauth2Login`); **`/ws/**` requires authentication**; remove `/actuator/**`; delete `CorsConfigurer`. Acceptance: tests for each route; `ALLOWED_ORIGINS` still drives CORS.
5. `fix: reject rest calls from banned users` (F-13). `OncePerRequestFilter` after authentication: banned -> 403 JSON `{"code":"USER_BANNED"}`; allow `GET /me`, `/logout`, `/ping`, `/login/**`, `/oauth2/**` so the frontend can still show the reason. One primary-key lookup per request, no caching (bans must be immediate). Acceptance: banned user -> 403 on `POST /friends/{id}/messages`, 200 on `GET /me`.
6. `fix: bound rate limiter memory and key anonymous traffic by client ip` (F-16). `ConcurrentHashMap` + scheduled purge of expired windows (`@EnableScheduling` only if absent), document "per instance". Acceptance: unit test that expired keys are evicted and that the limit still applies.
7. `fix: stop returning internal exception messages to clients` (F-15). Add `BadRequestException`/`ConflictException`; raw `IllegalArgumentException`/`IllegalStateException` -> fixed message + error log; update `GlobalExceptionHandlerTest`. Check callers that rely on the old behaviour (`grep "IllegalStateException\|IllegalArgumentException" src/main`) and convert those that are meant for clients (`FriendsController`, `DirectMessageService`) **only if** they are in your files; otherwise tell S2/S3b through the final report.

### S2 - Identity, moderation, users (agent `identity`)
Owns: `security/{SecurityAspect,RoleService,AppOidcUser,CustomOidcUserService,RequireRole}.java`, `endpoint/rest/controller/{ReportController,AdminModerationController,UserController}.java`, `endpoint/rest/dto/CreateReportDto.java` (+ new DTOs), `repository/ReportRepository.java`, `model/Report.java`, migration `V12`, `src/test/**/security/**`.
1. `fix: resolve the admin role from the authenticated principal` (F-07). `SecurityAspect` uses `authentication.getName()`. Add a real test through a proxied bean (MockMvc or `AopTestUtils`) proving an admin gets 200 and a normal user 403 on `/admin/reports`.
2. `fix: read admin e-mails from the ADMIN_EMAILS environment variable` (F-08). `@Value("${ADMIN_EMAILS:}")` directly in `RoleService` (do not edit `application.yml`, S1 owns it); split on comma, trim, lowercase, ignore blanks; remove the hardcoded set and `getAdminEmails()` leakage if unused. Tests: empty, one, several with spaces and mixed case.
3. `fix: reject google sign-ins whose e-mail is not verified` (F-08). `CustomOidcUserService` throws `OAuth2AuthenticationException` when `email_verified` is false or the e-mail is missing.
4. `fix: keep the country chosen by the user across logins` (F-25). Set `countryCode` only when the user is new or it is null.
5. `fix: restrict user endpoints to a public profile and the account owner` (F-11). `GET /users/{id}` -> DTO `{id, displayName, avatarUrl, countryCode}`; delete `POST /users/register` and `PUT /users/{id}/session` after `grep -rn findBySessionId src/main` (if a server-side caller exists keep the endpoint but require `principal.getName().equals(userId)`). Acceptance: no REST response contains another user's e-mail.
6. `fix: derive the report author from the session and validate report targets` (F-06). Ignore `reporterId` (remove its `@NotBlank` from `CreateReportDto`; unknown JSON fields are tolerated by the mapper), use `Principal`; reported user must exist, not self, `sessionId` (if present) must exist and include the reporter; `@Transactional`; **remove the auto-ban**; migration `V12__unique_report_per_session.sql` deduplicates then adds a unique index on `(reporter_id, reported_user_id, session_id)` (use a partial/expression approach that handles NULL `session_id`, e.g. `COALESCE(session_id,'')`).
7. `feat: list users reported by several distinct people for admin moderation` (decision "moderation queue"). `GET /admin/reports/flagged?minReporters=3&days=7` (`@RequireRole(ADMIN)`) returns `[{userId, distinctReporters, reportCount, lastReportAt}]` ordered by distinct reporters desc, via a `ReportRepository` query (`COUNT(DISTINCT reporterId)`). Test the query logic.
8. `fix: restrict the pending reports list to admins` (F-12) `@RequireRole(ADMIN)` on `GET /reports/pending`.
9. `fix: validate and de-duplicate admin report status updates` (F-14). `@NotNull` status, no-op when unchanged, side effects (strike, notification) only on a transition to `ACTIONED`.
10. `fix: end the active chat of a user when an admin bans them` (F-13). In `updateUserBan`, call `matchmakingService.endChat(userId)` after setting the ban (signature kept stable).

### S3a - Realtime / WebSocket (agent `realtime`)
Owns: `websocket/**` (controller, config, models, new interceptors/listeners) and tests under `src/test/**/websocket/**`.
1. `fix: relay webrtc signals only between users in an active session` (F-09). Reuse the active-session lookup used by `/chat` (extract a private helper); missing session -> `sendErrorToUser(senderId, "NO_ACTIVE_SESSION")`, do not relay. Also enforce a max payload size for `SignalMessage.data` as a string/size guard if cheap.
2. `fix: cap websocket frame size and per user message rate` (F-09). `WebSocketTransportRegistration.setMessageSizeLimit(64 * 1024)`; `ChannelInterceptor` on `clientInboundChannel` limiting each user to e.g. 100 SEND frames / 10 s (ICE candidates burst; chat is separately capped by length), over limit -> drop + `RATE_LIMITED` error to the user. Unit-test the interceptor.
3. `fix: put users back in the matching queue when they press next` (F-20). `/next`: after `endChat`, call `startSearching(userId, <current user's searchMode>)` before matching (read the mode from `UserRepository`; do not change `UserRepository`).
4. `feat: release users and notify peers when a websocket session disconnects` (F-22). `@EventListener SessionDisconnectEvent`: keep a `ConcurrentHashMap<userId, activeSessionCount>` updated on `SessionConnectedEvent`/`SessionDisconnectEvent`; when the **last** session of a user closes, schedule `endChat` after a 10 s grace period (cancelled if the user reconnects) using a `TaskScheduler`. Test with a fake scheduler.
5. `fix: reject chat messages longer than 2000 characters` (F-18). Trim, drop blanks (already done), reject longer with error code `MESSAGE_TOO_LONG`.

### S3b - Matching domain and persistence (agent `domain`)
Owns: `service/**` (except `RateLimiterService`), `repository/**` (except `ReportRepository`), `model/**` (except `Report`), `endpoint/rest/controller/{SessionController,FriendsController,MeController,NotificationController}.java`, `endpoint/rest/dto/{UserPreferencesDto,UpdateCountryDto}.java`, migration `V13`, tests under `src/test/**/service/**`.
1. `fix: claim matches atomically so a user never lands in two sessions` (F-19, F-23, F-24). In one `@Transactional` method: lock the caller and the chosen candidate rows ordered by id (`@Lock(PESSIMISTIC_WRITE)` query `findAllByIdInOrderById`), re-check both are `SEARCHING` and not banned after locking, apply the reputation (> -50) filter to **both** the scored pick and the fallback, close any stale ACTIVE sessions of both users before creating the new one, then set both `IN_CHAT`. Why ordered locking: claiming only "myself then the candidate" lets two users who pick each other both fail. Keep `findRandomMatch(String)` signature. Tests with Mockito for the decision logic; if Docker is available add a Testcontainers concurrency test, otherwise skip it with `disabledWithoutDocker`.
2. `fix: end the session for both users and notify the peer when a chat ends` (F-21). `endChat(userId)`: mark the ACTIVE session `ENDED`, set **both** users `IDLE` (not an already-banned/OFFLINE one), send `SignalType.PEER_DISCONNECTED` to the peer on `/queue/match` (inject `SimpMessagingTemplate`; update `MatchmakingServiceTest` constructor).
3. `fix: make feedback transactional and accept it only for ended sessions` (F-26). `@Transactional`; unique-constraint violation -> return `false`; reject non-ENDED sessions (409 via `ConflictException` if S1 merged it, otherwise `IllegalStateException` with a clear message); re-check mutual like after the write so simultaneous likes still create the friendship.
4. `fix: avoid lost updates on user reputation counters and partial saves` (F-27). `@DynamicUpdate` on `User`; atomic `@Modifying @Query` updates for reputation/strike counters in `ReputationService` (clamp score to the existing bounds, evict `CachedReputationService` as today). No `@Version`.
5. `fix: bound the size of user preference inputs` (F-18). `@Size(max = 10)` on lists, item `@Size`/`@Pattern`, `UpdateCountryDto` `@Pattern("^[A-Za-z]{2}$")` (normalize to upper case in the service), DM content max 2000 in `DirectMessageService.sendMessage`.
6. `perf: index chat sessions by second user and paginate list endpoints` (F-28). `V13__index_chat_session_user2.sql` (`CREATE INDEX IF NOT EXISTS`), `Pageable`/`limit` (default 50, max 100) on `/friends`, DM history and session message history. Keep the JSON array shape (no wrapper object) so the frontend stays simple; add `?before=<ISO instant>` cursor only if trivial.

### S4 - Documentation and contract (agent `docs`, starts after S1-S3 merged or at +50 min)
Owns: `README.md`, `docs/**` except `PLAN_PRODUCTION.md`/`HANDOFF.md`, root `*.md` deployment notes, `doc/api.yml`.
Audit each existing doc **against the code**, rewrite or delete what is wrong: `README.md`, `docs/backend-api.md`, `docs/ADMIN_SECURITY.md`, `docs/CACHE.md`, `docs/SWAGGER.md`, `DEPLOYMENT_NOTES.md`, `READY_FOR_DEPLOYMENT.md`, `DEPLOY_CHECKLIST.md`, `TESTING_GUIDE.md`, `TODO.md`, `doc/api.yml`.
1. `docs: publish the rest and websocket contract for the frontend` -> `docs/API.md` (priority 1; frontend is waiting): auth flow (`/oauth2/authorization/google`, cookie, `XSRF-TOKEN`/`X-XSRF-TOKEN`, 401 JSON, `USER_BANNED`), every REST endpoint with request/response JSON, STOMP destinations (`/app/search|signal|chat|dm|next|stop`; `/user/queue/match|signal|chat|dm`), all `SignalMessage` types and error codes, rate limits and size limits. Generate from the code, not from old docs.
2. `docs: rewrite the readme and local setup to match the current stack`.
3. `docs: document architecture, matchmaking flow and data model` -> `docs/ARCHITECTURE.md`.
4. `docs: add a deployment runbook for render, neon and github environments` -> `docs/DEPLOYMENT.md` (env vars table, Neon URL format, Google OAuth redirect URI `https://<render-host>/login/oauth2/code/google` for each environment, GitHub environments/secrets/vars `RENDER_DEPLOY_HOOK_URL`, `APP_URL`, `PROD_URL`, keepalive, rollback, promotion).
5. `docs: replace the admin security guide with an accurate security and moderation guide` -> `docs/SECURITY.md`.
6. `docs: remove obsolete deployment and testing notes` (only after the replacements exist).

## 4. Integration and release (lead, after the streams)
1. Fresh clone of `origin/preprod`: `./gradlew clean test`, `./format.sh && git diff --exit-code`, `docker build`.
2. Run the app locally against a Postgres container (Flyway on an empty DB), check: `/ping` 200; JSON call without session -> 401; browser-style `/oauth2/authorization/google` redirects with the right redirect URI; `/swagger-ui` closed with profile `prod`.
3. Check GitHub Actions on `preprod` is green (tests, format, image build; deploy job red only because the Render hook secret is not created yet).
4. Owner actions (cannot be automated, listed in `docs/DEPLOYMENT.md`): create two Neon projects, two Render services (free), GitHub environments `preprod` and `prod` with `DOCKERHUB_*` and `RENDER_DEPLOY_HOOK_URL`, repo variables `APP_URL`/`PROD_URL`, Render env vars (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `FRONTEND_URL`, `ALLOWED_ORIGINS`, `ADMIN_EMAILS`, `SPRING_PROFILES_ACTIVE=prod`, optional `SESSION_COOKIE_SAME_SITE`), Render health check path `/ping` and auto-deploy off, Google redirect URIs, branch protection requiring CI.
5. Only after the owner says go: PR `preprod -> prod`.

## 5. Commands
- Test: `$SLOT test` (see rule 8). Format: `./format.sh`. Format check: `./format.sh && git diff --exit-code`.
- Local DB: `docker compose up -d db` (Postgres on `localhost:55432`, db `raibu`, user `raibu_admin`, password `raibu_password`); app env: `DATABASE_URL=jdbc:postgresql://localhost:55432/raibu DATABASE_USERNAME=raibu_admin DATABASE_PASSWORD=raibu_password`.

## 6. Definition of done
- All of: CI green on `preprod`, app boots on an empty Postgres through Flyway, admin endpoints work for `ADMIN_EMAILS` and 403 otherwise, no endpoint leaks another user's e-mail, signaling only inside active sessions, one active session per user, bans enforced on REST and WebSocket, API contract published, no secret in git.
