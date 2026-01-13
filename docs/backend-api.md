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
    "gender": "FEMALE",          // Optional gender, free string (recommended: MALE | FEMALE | NON_BINARY | OTHER)
    "banned": false,
    "status": "IDLE"             // IDLE | SEARCHING | IN_CHAT | OFFLINE
  }
  ```

### `GET /sessions/{sessionId}/messages`

Returns the text messages exchanged in a given chat session. Only participants of the session can access the messages.

- **Method**: `GET`
- **Auth**: required
- **Path params**:
  - `sessionId`: ID of the `ChatSession`
- **Responses**:
  - `200` – array of `ChatMessage`:
    ```json
    [
      {
        "id": "string",
        "sessionId": "string",
        "senderId": "string",
        "content": "Hi there!",
        "sentAt": "2024-01-01T12:00:10Z"
      }
    ]
    ```
  - `401` – unauthenticated
  - `403` – user is not a participant of the session
  - `404` – session not found

### `POST /sessions/{sessionId}/feedback`

Submits simple feedback (like / dislike) for a given session. Only participants of the session can submit feedback.

- **Method**: `POST`
- **Auth**: required
- **Path params**:
  - `sessionId`: ID of the `ChatSession`.
- **Body (JSON)**:
  ```json
  {
    "liked": true
  }
  ```
- **Responses**:
  - `200` – feedback recorded (idempotent if the user already submitted feedback for this session).
  - `401` – unauthenticated.
  - `403` – user is not a participant of the session.
  - `404` – session not found.

Internally, this feedback is used to maintain a simple reputation signal for users (positive/negative experiences, strikes) and will also be reused for mutual matches / friends features.

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

### `GET /me/preferences`

Returns the matchmaking-related preferences for the authenticated user.

- **Method**: `GET`
- **Auth**: required
- **200 response** (example):
  ```json
  {
    "countryCode": "FR",
    "preferredLanguages": ["fr", "en"],
    "interests": ["gaming", "music"],
    "preferredGenders": ["MALE", "FEMALE"],
    "preferSameCountry": true
  }
  ```

### `PUT /me/preferences`

Updates the matchmaking-related preferences for the authenticated user.

- **Method**: `PUT`
- **Auth**: required
- **Body (JSON)** (all fields optional):
  ```json
  {
    "preferredLanguages": ["fr", "en"],
    "interests": ["gaming", "music"],
    "preferredGenders": ["FEMALE"],
    "preferSameCountry": false,
    "gender": "MALE"
  }
  ```
- **Rules**:
  - If a list is provided as empty (`[]`), the corresponding preference is cleared.
  - If a field is omitted, it is not changed.
  - `gender` is a free string; the frontend should stick to a controlled set of values.

## Users

### `GET /users/{userId}`

- **Method**: `GET`
- **Returns** full information for a user by their ID.

## Sessions

### `GET /sessions/me`

Returns the chat session history for the currently authenticated user.

- **Method**: `GET`
- **Auth**: required
- **200 response** (example):
  ```json
  [
    {
      "id": "string",
      "user1Id": "string",
      "user2Id": "string",
      "startedAt": "2024-01-01T12:00:00Z",
      "endedAt": "2024-01-01T12:05:00Z",
      "status": "ENDED"
    }
  ]
  ```

### `GET /friends`

Returns the list of friends (mutual matches) for the currently authenticated user.

- **Method**: `GET`
- **Auth**: required
- **200 response** (example):
  ```json
  [
    {
      "userId": "other-user-id",
      "createdAt": "2024-01-01T12:34:56Z"
    }
  ]
  ```
- **Notes**:
  - A friendship is created when **both** participants of a `ChatSession` submit `liked = true` feedback.
  - When a friendship is created, both users receive a `MATCH_CONFIRMED` notification.

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
  - Client sends a message to destination: `/app/search`.
  - Payload can be empty (for fully random mode) or include an optional `mode` field:
    ```json
    {
      "mode": "english_practice" // or "gaming", "coworking", "travel", "music", "movies_series", "food", "study", etc.
    }
    ```
  - The backend uses the authenticated user (ID from Google) as `userId` and remembers the selected `searchMode` while the user is searching / skipping.

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

- **Text chat during a session**:
  - The client can send text messages while the video chat is running.
  - **Send**: `/app/chat` with a `ChatInboundMessage`:
    ```json
    {
      "to": "<otherUserId>",
      "content": "Hello!"
    }
    ```
  - The backend checks:
    - sender is not banned,
    - there is an active `ChatSession` between the two users.
  - Messages are persisted as `ChatMessage` rows and broadcast as `SignalMessage` on `/user/queue/chat` for **both** users:
    ```json
    {
      "type": "CHAT_TEXT",
      "from": "<senderId>",
      "to": "<recipientId>",
      "data": {
        "sessionId": "<sessionId>",
        "senderId": "<senderId>",
        "content": "Hello!",
        "sentAt": "2024-01-01T12:00:10Z"
      }
    }
    ```

- **Icebreakers**:
  - After a match is established, the backend may send an optional `ICEBREAKER` `SignalMessage` on `/user/queue/match` to both users, containing a simple question to help start the conversation:
    ```json
    {
      "type": "ICEBREAKER",
      "from": "system",
      "to": "<userId>",
      "data": "What's something small that made you smile recently?"
    }
    ```
  - Icebreakers depend on the selected `mode` when provided (e.g. different friendly questions for `english_practice`, `gaming`, `coworking`, `travel`, `music`, `movies_series`, `food`, `study`, etc.).
- **Banned users**:
  - When a banned user tries to search or go to the next user, the backend will not start matchmaking.
  - Instead, it sends an `ERROR` `SignalMessage` on `/user/queue/match`:
    ```json
    {
      "type": "ERROR",
      "from": "system",
      "to": "<currentUserId>",
      "data": "USER_BANNED"
    }
    ```

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

## Notifications

### REST: `GET /notifications`

Returns the notifications for the currently authenticated user.

- **Method**: `GET`
- **Auth**: required
- **Query params** (optional):
  - `unread` (`boolean`): if `true`, returns only unread notifications.
  - `limit` (`number`): max number of notifications to return (1–100, default ≈ 50).
- **200 response** (example):
  ```json
  [
    {
      "id": "string",
      "type": "USER_BANNED",
      "message": "Your account has been banned.",
      "data": null,
      "createdAt": "2024-01-01T12:00:00Z",
      "read": false
    }
  ]
  ```

### REST: `PUT /notifications/{notificationId}/read`

Marks a specific notification as read for the authenticated user.

- **Method**: `PUT`
- **Auth**: required
- **Path params**:
  - `notificationId`: ID of the notification to mark as read.
- **Responses**:
  - `200` – notification marked as read (idempotent if already read).
  - `404` – notification not found or does not belong to the current user.

### WebSocket: `/user/queue/notifications`

- The client should subscribe to `/user/queue/notifications` over STOMP.
- On the backend, notifications are sent as `SignalMessage`-like payloads specialized for notifications:
  ```json
  {
    "id": "string",
    "type": "REPORT_ACTIONED",      // USER_BANNED | USER_UNBANNED | REPORT_ACTIONED
    "message": "One of your reports has been processed.",
    "data": "{\"reportId\":\"...\"}",
    "createdAt": "2024-01-01T12:34:56Z",
    "read": false
  }
  ```

### When notifications are emitted

Currently, the backend emits notifications for the following events:

- **User banned by admin** (`PUT /admin/users/{userId}/ban` with `banned = true`):
  - Type: `USER_BANNED`.
  - Message: `"Your account has been banned."`.
- **User unbanned by admin** (`PUT /admin/users/{userId}/ban` with `banned = false`):
  - Type: `USER_UNBANNED`.
  - Message: `"Your account has been unbanned."`.
- **Report processed by admin** (`PUT /admin/reports/{reportId}/status` to `ACTIONED`):
  - Type: `REPORT_ACTIONED`.
  - Message: `"One of your reports has been processed."`.
  - `data` contains a small JSON string with the `reportId`.
- **Auto-ban after multiple reports** (when a user reaches the report threshold and is auto-banned):
  - Type: `USER_BANNED`.
  - Message: `"Your account has been banned due to multiple reports."`.

The frontend can rely on these notifications (via WebSocket or the REST list) to display in-app banners, toasts, or badges when important moderation-related events happen.
