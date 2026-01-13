# API Backend Raibu

This document summarizes the main backend endpoints used by the frontend.

## Authentication and user context

### `GET /me`

Returns the currently authenticated user (via Google OAuth2).

- **Method**: `GET`
- **Auth**: required (will redirect to Google if not authenticated)
- **200 response**:
  ```json
  {
    "id": "string",              // Internal Raibu ID (UUID)
    "displayName": "string",     // Display name (from Google)
    "email": "string",           // Google email
    "avatarUrl": "string",       // Google avatar URL
    "countryCode": "FR",         // Country code (derived from Google locale or set by the user)
    "banned": false,
    "status": "IDLE"             // IDLE | SEARCHING | IN_CHAT | OFFLINE
  }
  ```

### `PUT /me/country`

Allows the authenticated user to update their preferred country.

- **Method**: `PUT`
- **Auth**: required
- **Body (JSON)**:
  ```json
  { "countryCode": "FR" }
  ```
  - The code is normalized to upper case on the backend.
- **Responses**:
  - `200`: country updated
  - `400`: `countryCode` missing or empty
  - `401`: unauthenticated
  - `404`: user not found (should not normally happen)

## Users

### `GET /users/{userId}`

- **Method**: `GET`
- **Returns** full information for a user by their ID.

## Health / utilities

### `GET /ping`

- Simple healthcheck endpoint, not protected.
- Returns `"pong"`.

## User-side moderation

### `POST /reports`

Create a report against another user.

- **Method**: `POST`
- **Body (JSON)**:
  ```json
  {
    "reporterId": "string",       // ID of the user who is reporting
    "reportedUserId": "string",   // ID of the user being reported
    "sessionId": "string",        // ID of the chat session (optional)
    "reason": "INAPPROPRIATE_BEHAVIOR", // see enum below
    "description": "string"       // free text
  }
  ```
- **Possible reasons** (`Report.ReportReason`):
  - `INAPPROPRIATE_BEHAVIOR`
  - `NUDITY`
  - `HARASSMENT`
  - `SPAM`
  - `OTHER`
-- **Backend behavior**:
  - Creates a `Report` with `status = PENDING`.
  - Counts how many reports exist for `reportedUserId`.
  - If `>= 3`, sets `user.banned = true` (auto-ban).

### `GET /reports/pending`

- Returns the list of reports with `status = PENDING`.

## Admin moderation

The endpoints below are meant for an admin backoffice. They are currently protected by Google auth but **not yet restricted to a dedicated admin role** (to be refined later).

Base path: `/admin`

### `GET /admin/reports`

List reports.

- **Optional query param**: `status`
  - examples: `PENDING`, `REVIEWED`, `ACTIONED`.
- **Examples**:
  - `GET /admin/reports` → all reports
  - `GET /admin/reports?status=PENDING` → only pending reports

### `GET /admin/users/{userId}/reports`

- Lists all reports where `reportedUserId = {userId}`.

### `PUT /admin/reports/{reportId}/status`

Update the status of a report.

- **Body (JSON)**:
  ```json
  { "status": "REVIEWED" }
  ```
- **Allowed statuses** (`Report.ReportStatus`):
  - `PENDING`
  - `REVIEWED`
  - `ACTIONED`

### `GET /admin/users/{userId}/sessions`

Returns the chat session history for a user.

- Each element is a `ChatSession`:
  - `id`, `user1Id`, `user2Id`, `startedAt`, `endedAt`, `status` (`ACTIVE` | `ENDED`).

### `PUT /admin/users/{userId}/ban`

Ban or unban a user.

- **Body (JSON)**:
  ```json
  { "banned": true }
  ```
-- **Effect**:
  - updates `user.banned`.
  - banned users are no longer considered by the matchmaking.

## Matchmaking & WebSocket

### WebSocket connection

- SockJS/STOMP endpoint: `/ws`
- STOMP prefixes:
  - Send: `/app/...`
  - Subscriptions: `/topic/...` and `/user/queue/...`

### Main STOMP flow

- **Search for a match**:
  - Client sends an empty message (payload is ignored) to destination: `/app/search`.
  - The backend uses the authenticated user (ID from Google) as `userId`.

- **Receive a match**:
  - Subscribe to `/user/queue/match`.
  - Message of type `SignalMessage`:
    ```json
    {
      "type": "MATCH_FOUND",
      "from": "system",
      "to": "<currentUserId>",
      "data": "<matchedUserId>"
    }
    ```

- **Next user**:
  - Send to `/app/next` (payload ignored, user taken from authentication).

- **Stop searching**:
  - Send to `/app/stop` (payload ignored).

- **WebRTC signaling**:
  - Send signaling messages to `/app/signal` with a `SignalMessage`:
    ```json
    {
      "type": "OFFER" | "ANSWER" | "ICE_CANDIDATE",
      "from": "<currentUserId>",
      "to": "<otherUserId>",
      "data": { ... }   // SDP or ICE
    }
    ```
  - The backend relays to `/user/queue/signal` for the recipient.

## Rate limiting (important for the frontend)

A simple rate limiting mechanism is in place on the backend:

- 1-minute window per **authenticated user** (or per IP if not authenticated).
- Sensitive write operations (`POST`/`PUT`/`DELETE` on `/reports`, `/me`, `/users`):
  - limit ≈ 30 requests / minute / user.
- Other endpoints:
  - limit ≈ 300 requests / minute / user.
- When exceeded: **HTTP 429 Too Many Requests**.

The frontend should therefore:

- avoid fast loops on these endpoints,
- handle 429 responses gracefully (wait, then retry later if needed).
