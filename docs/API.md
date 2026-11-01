# Raibu API contract

REST + WebSocket (STOMP) contract for the frontend. Generated from the code on `preprod`, not from older docs.
Everything marked **unverified** was read in the code but never exercised against a running instance.

Base URL: `https://<render-host>` (unknown until deployment; locally `http://localhost:8080`). Nothing is hardcoded in the API.
All timestamps are ISO-8601 instants (`2026-11-01T12:00:00Z`). All ids are UUID strings. `Principal.getName()` is the **app user id**
(the `id` returned by `GET /me`), not the Google `sub`.

## 1. Authentication

Login is Google OIDC (authorization code flow) with a server-side **session cookie** (`JSESSIONID`). There is no token and no
password.

1. Navigate the browser (full page load, not `fetch`) to `GET {API}/oauth2/authorization/google`.
2. Google authenticates the user and redirects to `{API}/login/oauth2/code/google` (handled by the backend).
3. On success the backend sets `JSESSIONID` and redirects to `{FRONTEND_URL}/home`. Sign-ins whose Google e-mail is missing or
   not verified are rejected (OAuth error, no session).
4. The frontend calls `GET /me` with `credentials: 'include'`. `200` = signed in.
5. Logout: `POST {API}/logout` (CSRF token required, see below). Redirects to `{FRONTEND_URL}/login`.

Cookie attributes: with `SPRING_PROFILES_ACTIVE=prod` the session cookie is `Secure` and `SameSite=None` (configurable with
`SESSION_COOKIE_SAME_SITE`, e.g. `lax` when the frontend shares the API's site). Locally (no profile) it is the servlet default (`Lax`, not
`Secure`). Every `fetch`/`XMLHttpRequest` must use `credentials: 'include'`; every SockJS connection sends the cookie automatically.

### 401 instead of a redirect

An unauthenticated request never redirects API clients to Google. The response is:

```
HTTP/1.1 401
Content-Type: application/json

{"code":"UNAUTHENTICATED"}
```

Only a browser navigation (request that explicitly accepts `text/html`, without `X-Requested-With: XMLHttpRequest`, and not under
`/ws/**`) is redirected (`302`) to `/oauth2/authorization/google`. `fetch()` with the default `Accept: */*` gets the 401.

### CSRF

State-changing requests (`POST`, `PUT`, `DELETE`, `PATCH`, including `/logout`) need a CSRF token (double-submit cookie):

- The backend writes a cookie `XSRF-TOKEN` (not HttpOnly, path `/`, same `SameSite`/`Secure` policy as the session cookie) on every response of a
  signed-in session, so a plain `GET /me` issues it.
- Send the token value back in the header `X-XSRF-TOKEN` on every `POST`/`PUT`/`DELETE`.
- **Cross-site frontend:** when the SPA and the API are on different sites, `document.cookie` cannot read the API's cookie. Call
  `GET {API}/csrf` (session required) once after login (and after any `403 CSRF_INVALID`): it returns
  `{"headerName":"X-XSRF-TOKEN","parameterName":"_csrf","token":"<value>"}`. Send `token` in `headerName`. Same-site frontends can also read the cookie.
- A missing or wrong token gives `403` `{"code":"CSRF_INVALID"}`. Other access-denied cases give `403` `{"code":"FORBIDDEN"}`.
- `/ws/**` (SockJS handshake and STOMP) is exempt from CSRF.

### Banned users

A banned user (`banned: true` in `GET /me`) is blocked from the REST API and from every STOMP action:

- REST: `403` with `{"code":"USER_BANNED"}` on every endpoint except `GET /me`, `/logout`, `/ping`, `/login/**`, `/oauth2/**`,
  so the frontend can still read `banned` and show a message. (See section 9: ban filter status.)
- STOMP: the frame is dropped and an `ERROR` message with `data: "USER_BANNED"` is sent on `/user/queue/match`.
- When an admin bans a user in an active chat, the chat is ended and the peer receives `PEER_DISCONNECTED` (see section 9).

### CORS

`ALLOWED_ORIGINS` (comma separated list of exact origins, no trailing slash, default
`http://localhost:5173,http://localhost:3000,https://raibu.app`) drives both REST CORS and the WebSocket allowed origins.
Allowed methods `GET, POST, PUT, DELETE, OPTIONS`, all headers, credentials allowed. The frontend origin **must** be in the list.

### Error shapes

| Case | Status | Body |
|---|---|---|
| Not authenticated | 401 | `{"code":"UNAUTHENTICATED"}` |
| Banned user | 403 | `{"code":"USER_BANNED"}` |
| CSRF missing/invalid | 403 | `{"code":"CSRF_INVALID"}` |
| Insufficient role (`@RequireRole`) | 403 | `{"timestamp","status":403,"error":"Forbidden","message":"Insufficient permissions"}` |
| Bad argument / state (`IllegalArgumentException`, `IllegalStateException` from services) | 400 | `{"timestamp","status":400,"error":"Bad Request","message":"<text>"}` |
| Not found | 404 | usually empty body; `ResourceNotFoundException` gives the JSON shape above |
| Bean validation failure on a `@Valid` body | 400 | Spring `ProblemDetail`-style JSON (unverified exact shape) |
| Rate limit exceeded | 429 | empty body |
| Unexpected | 500 | `{"timestamp","status":500,"error":"Internal Server Error","message":"An unexpected error occurred"}` |

Messages of the 400 shape come from the service layer and may change: switch on the status code, not on the text.

### REST rate limits

Per authenticated user id (per client IP when anonymous), fixed window of 60 s, in memory (per instance):

- `POST`/`PUT`/`DELETE` on paths starting `/reports`, `/me`, `/users`: **30 per minute**.
- Everything else: **300 per minute**.
- `/ping` and `/ws/**` are not limited by this filter (the WebSocket has its own limit, section 4).

Over the limit: `429`, empty body.

## 2. Public and utility endpoints

| Method | Path | Auth | Response |
|---|---|---|---|
| GET | `/ping` | none | `200` text `pong`. Used for health checks and keepalive. |
| GET | `/health`, `/health/live`, `/health/ready` | session | `200` `{"status":"UP","service":"raibu-backend","timestamp":"..."}`, `{"status":"alive"}`, `{"status":"ready"}`. Not for infrastructure health checks (they need a session); use `/ping`. |
| GET | `/swagger-ui/index.html`, `/v3/api-docs` | session | Only when profile `prod` is **not** active (disabled in prod). |

## 3. REST endpoints

All endpoints below require a session unless stated. `{id}` values are user/session UUIDs.

### Current user

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/me` | none | `200` `MeResponse` or `404` if the user row is missing |
| GET | `/me/preferences` | none | `200` `PreferencesResponse` |
| PUT | `/me/preferences` | `PreferencesRequest` (all fields optional) | `200` empty |
| PUT | `/me/country` | `{"countryCode":"FR"}` (exactly 2 chars; stored upper-case) | `200` empty, `400` if invalid |

```jsonc
// MeResponse
{ "id": "uuid", "displayName": "Ada", "email": "ada@example.com", "avatarUrl": "https://...",
  "countryCode": "FR", "gender": "female", "banned": false,
  "status": "IDLE" }          // IDLE | SEARCHING | IN_CHAT | OFFLINE

// PreferencesResponse
{ "countryCode": "FR", "preferredLanguages": ["en","fr"], "interests": ["music"],
  "preferredGenders": ["female"], "preferSameCountry": true }

// PreferencesRequest: omitted/null fields are left unchanged
{ "preferredLanguages": ["en","fr"], "interests": ["music","gaming"], "preferredGenders": ["female"],
  "preferSameCountry": true, "gender": "male" }
```

Notes: `email` is returned only for the caller's own profile. Lists are stored as comma separated text, so list items must not contain a
comma. Limits (list size, item length, country pattern) are being tightened, see section 9. Sending an empty list leaves the
stored value unchanged.

### Users

| Method | Path | Response |
|---|---|---|
| GET | `/users/{userId}` | `200` `{"id","displayName","avatarUrl","countryCode"}` (public profile, never e-mail) or `404` |

`POST /users/register` and `PUT /users/{id}/session` no longer exist.

### Sessions (chat history and feedback)

A session is one pairing of two users. `MATCH_FOUND` does not carry the session id: get it from `GET /sessions/me` (look for
`status: "ACTIVE"`) or from `CHAT_TEXT.data.sessionId`.

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/sessions/me` | none | `200` `ChatSession[]` where the caller is `user1Id` or `user2Id` |
| GET | `/sessions/{sessionId}/messages` | none | `200` `ChatMessage[]` oldest first; `404` unknown session; `403` caller not in the session |
| POST | `/sessions/{sessionId}/feedback` | `{"liked": true}` | `200` empty; `404`; `403` not a participant; `409` session still `ACTIVE` |

```jsonc
// ChatSession
{ "id": "uuid", "user1Id": "uuid", "user2Id": "uuid", "startedAt": "...", "endedAt": null, "status": "ACTIVE" } // ACTIVE | ENDED
// ChatMessage
{ "id": "uuid", "sessionId": "uuid", "senderId": "uuid", "content": "hello", "sentAt": "..." }
```

Feedback: only once the session is `ENDED` (end the chat with `/app/next` or wait for `PEER_DISCONNECTED` first). One per user per session (a second submit returns `200` and is ignored). `liked: true` gives the peer +1 reputation, `false` gives -3
(clamped to [-100, 100]). When **both** users liked each other in the same session a friendship is created and both receive a
`MATCH_CONFIRMED` notification.

### Friends and direct messages

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/friends` | none | `200` `[{"userId":"uuid","createdAt":"..."}]` (`userId` is the friend; fetch the profile with `/users/{id}`) |
| GET | `/friends/{friendId}/messages` | none | `200` `DirectMessage[]` oldest first; `403` if not friends |
| POST | `/friends/{friendId}/messages` | `{"content":"hi"}` | `200` `DirectMessage`; `400` blank/self; `403` not friends |

```jsonc
// DirectMessage
{ "id": "uuid", "senderId": "uuid", "recipientId": "uuid", "content": "hi", "sentAt": "..." }
```

Content is trimmed. Maximum length and pagination: see section 9. Sending a DM creates a `NEW_DM` notification for the recipient.
The same DM can be sent in real time over STOMP (`/app/dm`).

### Notifications

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/notifications?unread=true&limit=50` | both params optional (`limit` clamped to 1..100, default 50) | `200` `Notification[]`, newest first |
| PUT | `/notifications/{notificationId}/read` | none | `200`, or `404` if unknown or not the caller's |

```jsonc
// Notification (same shape as the STOMP push on /user/queue/notifications)
{ "id": "uuid", "type": "NEW_DM", "message": "You have a new message.", "data": "{\"senderId\":\"...\",\"directMessageId\":\"...\"}",
  "createdAt": "...", "read": false }
```

`type` is one of `USER_BANNED`, `USER_UNBANNED`, `REPORT_ACTIONED` (`data` = `{"reportId"}`), `MATCH_CONFIRMED` (`data` = `{"friendId"}`),
`NEW_DM` (`data` = `{"senderId","directMessageId"}`). `data` is a JSON **string**: parse it.

### Reports

| Method | Path | Role | Request | Response |
|---|---|---|---|---|
| POST | `/reports` | user | `CreateReport` | `200` `Report`; `400` self-report or reported user not in the session; `403` caller not in the session; `404` unknown user or session; `409` already reported (same target and session) |
| GET | `/reports/pending` | admin (see section 9) | none | `200` `Report[]` with status `PENDING` |

```jsonc
// CreateReport
{ "reportedUserId": "uuid", "sessionId": "uuid or omitted", "reason": "HARASSMENT", "description": "max 1000 chars" }
// reason: INAPPROPRIATE_BEHAVIOR | NUDITY | HARASSMENT | SPAM | OTHER
// Report
{ "id": "uuid", "reporterId": "uuid", "reportedUserId": "uuid", "sessionId": null, "reason": "HARASSMENT",
  "description": null, "createdAt": "...", "status": "PENDING" } // PENDING | REVIEWED | ACTIONED
```

There is **no automatic ban**. The report author is the authenticated user (a `reporterId` in the body is ignored). The reported user must exist and differ from the caller; a `sessionId`, if given, must include both users. Users reported by at least 3 distinct people within 7 days appear in the admin queue.

### Admin (role `ADMIN`, else `403`)

Admins are the users whose Google e-mail is listed in the `ADMIN_EMAILS` environment variable (comma separated, case-insensitive).

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/admin/reports?status=PENDING` | `status` optional | `200` `Report[]` |
| GET | `/admin/reports/flagged?minReporters=3&days=7` | both optional | `200` `[{"userId","distinctReporters","reportCount","lastReportAt"}]` (see section 9) |
| GET | `/admin/users/{userId}/reports` | none | `200` `Report[]` |
| GET | `/admin/users/{userId}/sessions` | none | `200` `ChatSession[]` |
| PUT | `/admin/reports/{reportId}/status` | `{"status":"ACTIONED"}` | `200` empty; `404` |
| PUT | `/admin/users/{userId}/ban` | `{"banned":true}` | `200` empty; `404` |

Moving a report to `ACTIONED` adds a strike and -20 reputation to the reported user and notifies the reporter (`REPORT_ACTIONED`).
Banning sets reputation to at most -50 and sends `USER_BANNED`; unbanning sends `USER_UNBANNED`.

## 4. WebSocket (STOMP over SockJS)

- Endpoint: `{API}/ws` with SockJS (`new SockJS(API + '/ws')`; use `https://` in production, the client upgrades to `wss://`).
  The browser sends the session cookie, so the user must be signed in first. Allowed origins = `ALLOWED_ORIGINS`.
- Broker prefixes: server -> client `/queue` and `/topic`; client -> server `/app`.
- Per-user messages arrive on `/user/queue/...` (subscribe to exactly that string).
- STOMP heartbeats are not configured server side (SockJS keeps the connection alive; unverified under Render's idle timeout).
- Every STOMP message payload is JSON with content type `application/json`.

### Subscriptions to open right after CONNECT

| Destination | Content |
|---|---|
| `/user/queue/match` | `MATCH_FOUND`, `ICEBREAKER`, `PEER_DISCONNECTED`, `ERROR` |
| `/user/queue/signal` | `OFFER`, `ANSWER`, `ICE_CANDIDATE`, `CALL_*` relayed from the peer |
| `/user/queue/chat` | `CHAT_TEXT` (both sender and recipient receive it) |
| `/user/queue/dm` | `DM_TEXT` (both sender and recipient receive it) |
| `/user/queue/notifications` | `Notification` (same shape as REST), pushed on every new notification |

### Send destinations

| Destination | Payload | Effect |
|---|---|---|
| `/app/search` | `{"mode":"gaming"}` (payload optional, `mode` optional) | Marks the user `SEARCHING` with that mode and tries to match immediately. |
| `/app/stop` | none | Leaves the queue (`IDLE`). |
| `/app/next` | none | Ends the current chat (peer is told), puts the caller back in the queue with their previous mode, tries to match immediately. |
| `/app/signal` | `SignalMessage` | Relays to `to` on `/user/queue/signal`. |
| `/app/chat` | `{"to":"<peerId>","content":"text"}` | Stores and delivers a chat message of the active session. |
| `/app/dm` | `{"to":"<friendId>","content":"text"}` | Stores and delivers a DM to a friend. |

`mode` is free text, trimmed and lower-cased. It biases matching (same mode +3) and selects the icebreaker list. Known modes for
icebreakers: `english_practice`, `gaming`, `coworking`, `travel`, `music`, `movies_series` (others fall back to generic questions).

### `SignalMessage` envelope

```jsonc
{ "type": "OFFER", "from": "<senderId>", "to": "<recipientId>", "data": <any JSON> }
```

`from` is always overwritten by the server with the real sender (cannot be spoofed). Messages created by the server use
`from: "system"`.

| `type` | Direction / queue | `data` |
|---|---|---|
| `MATCH_FOUND` | server -> both users, `/user/queue/match` | peer user id (string) |
| `ICEBREAKER` | server -> both users, `/user/queue/match`, right after `MATCH_FOUND` | question text (string) |
| `PEER_DISCONNECTED` | server -> remaining peer, `/user/queue/match` | id (string) of the user who ended the chat |
| `ERROR` | server -> sender, `/user/queue/match` | error code (string), see below |
| `OFFER` | client -> peer, via `/app/signal`, delivered on `/user/queue/signal` | SDP offer. Opaque to the server (any JSON, typically `{"type":"offer","sdp":"..."}`). |
| `ANSWER` | same | SDP answer |
| `ICE_CANDIDATE` | same | ICE candidate (`RTCIceCandidateInit`) |
| `CALL_INVITE`, `CALL_ACCEPT`, `CALL_REJECT`, `CALL_END` | same | Defined in the enum and relayed like any signal, but the server attaches no meaning. **Known limitation:** `/app/signal` only relays between users sharing an `ACTIVE` session, so these cannot be used for calls between friends once their random chat has ended (the reply is `NO_ACTIVE_SESSION`). Friend calls need a backend change. |
| `CHAT_TEXT` | server -> both users, `/user/queue/chat` | `{"sessionId","senderId","content","sentAt"}` (`from` = sender, `to` = recipient) |
| `DM_TEXT` | server -> both users, `/user/queue/dm` | `{"id","senderId","recipientId","content","sentAt"}` |

### Error codes (`ERROR`, `data` is the code)

| Code | Cause |
|---|---|
| `USER_BANNED` | The user is banned (search, signal, chat, dm, next). |
| `NO_ACTIVE_SESSION` | `/app/signal` or `/app/chat` to a user the caller shares no `ACTIVE` session with. Nothing is relayed. |
| `MESSAGE_TOO_LONG` | Chat content longer than 2000 characters after trimming. Nothing is stored. |
| `NOT_FRIENDS` | `/app/dm` to someone who is not a friend. |
| `RATE_LIMITED` | More than 100 SEND frames in 10 s. The frame is dropped. |

Silently ignored (no error): chat/DM with a blank recipient, null or blank content, signal with no `to`, DM to self or longer than allowed
(see section 9).

### Limits

- Max STOMP message size: **64 KB** (`64 * 1024` bytes). Larger frames close the connection (SockJS/Spring behavior, unverified client-side effect).
- Max **100 SEND frames per user per 10 s** (fixed window, per instance). ICE candidates count, so do not trickle more than that.
- Chat message: **2000 characters** after trim.

### Match lifecycle and disconnects

- `/app/search`: if nobody compatible is waiting the user stays `SEARCHING` silently. The match is created by whoever searches next:
  both receive `MATCH_FOUND` (and `ICEBREAKER`). There is no timeout and no server retry: keep the socket open and show "searching".
- Each user is in at most one active session at a time (matching is atomic).
- `/app/next` or the end of a chat: the session becomes `ENDED`, **both** users return to `IDLE` (the caller then re-enters the queue for `/next`),
  and the other user receives `PEER_DISCONNECTED` on `/user/queue/match`. On `PEER_DISCONNECTED` close the `RTCPeerConnection`,
  show the feedback form (`POST /sessions/{id}/feedback`) and let the user press search again.
- **Closing the socket** (tab closed, network lost): when the user's *last* WebSocket session closes, the server waits a **10 s grace
  period**; if the user has not reconnected, the chat is ended and the peer receives `PEER_DISCONNECTED`. A reconnect within 10 s
  (page reload, another tab) cancels the release. After reconnecting, re-subscribe to all queues; the server does not resend
  `MATCH_FOUND`, so use `GET /sessions/me` to detect an `ACTIVE` session.
- A user who only stops with `/app/stop` is `IDLE` and not matched.
- Ban by an admin: the chat of the banned user is ended (section 9).

### WebRTC signaling sequence

The server does not assign the offerer role. Use a deterministic rule on both sides, for example: the user whose id is
lexicographically smaller creates the offer.

```
A (offerer)                    server                          B
   | MATCH_FOUND(B) + ICEBREAKER |  MATCH_FOUND(A) + ICEBREAKER   |
   |<----------------------------+------------------------------->|
   | /app/signal OFFER to=B      |                                |
   |---------------------------->| /user/queue/signal OFFER from=A|
   |                             |------------------------------->|
   |                             | /app/signal ANSWER to=A        |
   | /user/queue/signal ANSWER   |<-------------------------------|
   |<----------------------------|                                |
   | /app/signal ICE_CANDIDATE to=B  (both directions, repeated)  |
   |<------------------------- relay ----------------------------->|
```

Use STUN/TURN servers of your choice: the backend provides none (ICE server config lives in the frontend). Signals are only relayed
while the two users share an `ACTIVE` session; otherwise the sender gets `NO_ACTIVE_SESSION`.

## 5. Matching rules (what the frontend can expect)

Candidates are other `SEARCHING`, non-banned users with reputation >= -49. They are scored and one of the best is picked at random:
same mode +3, same country +2 (unless `preferSameCountry` is false), shared language +1, shared interest +2, candidate gender in the
caller's `preferredGenders` +2, the caller's gender in the candidate's `preferredGenders` +2, plus `reputation / 10`. If no score is
positive a random candidate is used. Preferences only bias the choice, they never exclude anyone (except ban and reputation).

## 6. End-to-end example

```js
// 0. sign in (full page navigation)
location.href = `${API}/oauth2/authorization/google`;           // returns to ${FRONTEND_URL}/home

// 1. who am I (also issues the XSRF-TOKEN cookie)
//    cross-site SPA: const { headerName, token } = await (await fetch(`${API}/csrf`, { credentials: 'include' })).json();
const me = await (await fetch(`${API}/me`, { credentials: 'include' })).json();
if (me.banned) return showBanned();

// 2. connect
const client = Stomp.over(() => new SockJS(`${API}/ws`));
client.connect({}, () => {
  client.subscribe('/user/queue/match', onMatchEvent);           // MATCH_FOUND, ICEBREAKER, PEER_DISCONNECTED, ERROR
  client.subscribe('/user/queue/signal', onSignal);              // OFFER, ANSWER, ICE_CANDIDATE
  client.subscribe('/user/queue/chat', onChat);                  // CHAT_TEXT
  client.subscribe('/user/queue/dm', onDm);                      // DM_TEXT
  client.subscribe('/user/queue/notifications', onNotification);
  client.send('/app/search', {}, JSON.stringify({ mode: 'gaming' }));
});

// 3. MATCH_FOUND: data = peer id. The smaller id creates the offer.
function onMatchEvent(frame) {
  const m = JSON.parse(frame.body);
  if (m.type === 'MATCH_FOUND') {
    peerId = m.data;
    if (me.id < peerId) createOfferAndSend();                    // send '/app/signal' {type:'OFFER', to: peerId, data: sdp}
  } else if (m.type === 'PEER_DISCONNECTED') {
    closePeerConnection(); showFeedback();
  } else if (m.type === 'ERROR') {
    handleError(m.data);                                         // USER_BANNED, RATE_LIMITED, ...
  }
}

// 4. signaling: on ICE candidates send {type:'ICE_CANDIDATE', to: peerId, data: candidate} to '/app/signal'.

// 5. text chat during the session
client.send('/app/chat', {}, JSON.stringify({ to: peerId, content: 'hello' }));

// 6. next person (peer gets PEER_DISCONNECTED, caller is queued again and matched if possible)
client.send('/app/next', {});

// 7. feedback for the session that just ended (id from GET /sessions/me, the latest ENDED one)
await fetch(`${API}/sessions/${sessionId}/feedback`, {
  method: 'POST', credentials: 'include',
  headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': csrfToken }, // csrfToken from GET /csrf
  body: JSON.stringify({ liked: true })
});
// mutual likes -> friendship + MATCH_CONFIRMED notification on /user/queue/notifications

// 8. friends and DMs
const friends = await (await fetch(`${API}/friends`, { credentials: 'include' })).json();
client.send('/app/dm', {}, JSON.stringify({ to: friends[0].userId, content: 'hey!' }));
```

## 7. Data enums

`UserStatus`: `IDLE`, `SEARCHING`, `IN_CHAT`, `OFFLINE`. `SessionStatus`: `ACTIVE`, `ENDED`. `ReportReason`: see Reports.
`ReportStatus`: `PENDING`, `REVIEWED`, `ACTIONED`. `Role`: `USER`, `MODERATOR`, `ADMIN` (only `ADMIN` is ever assigned).

## 8. Unverified

- Exact JSON of bean-validation `400` errors.
- Behavior of the SockJS transport when a frame exceeds 64 KB and under Render's idle connection timeout.
- All flows were read from the code; none were run against a live instance.

## 9. Changes in flight

These are decided and being merged into `preprod` by other work streams. Until they land, the current behavior is given. Treat the
"target" column as the contract to code against; this section is removed once everything is deployed.

| Topic | Today on `preprod` | Target contract |
|---|---|---|
| Ban on REST | Only STOMP actions check the ban | `403 {"code":"USER_BANNED"}` on all REST except `GET /me`, `/logout`, `/ping`, `/login/**`, `/oauth2/**` |
| `/ws/**` auth | Handshake is public; an unauthenticated STOMP client has no principal and its frames are ignored | Handshake requires a session (`401` otherwise) |
| `GET /reports/pending` | Any signed-in user | Admin only |
| `GET /admin/reports/flagged` | Does not exist | Admin queue of users with >= `minReporters` (default 3) distinct reporters in the last `days` (default 7) |
| `PUT /admin/reports/{id}/status` | Accepts null, repeats side effects | `status` required; side effects only on the transition to `ACTIONED` |
| Admin ban | Sets the flag only | Also ends the user's active chat (peer gets `PEER_DISCONNECTED`) |
| Pagination | `/friends`, DM history, `/sessions/me`, session messages return everything | Same JSON array shape, `limit` (default 50, max 100) |
| Input limits | Lists and DM content unbounded (DB overflow gives `500`) | Preference lists max 10 items, DM content max 2000 characters |
| Error bodies | 400 bodies echo internal exception messages | Fixed messages, `409` for conflicts |

Check `gh pr list --state merged` on the repository for what has already landed.
