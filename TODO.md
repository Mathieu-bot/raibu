# Raibu Backend TODOs

## A. Preferences & Matchmaking v2 [DONE]

- Status: Implemented (backend & docs).
- Implement advanced user preferences (interests, languages, finer location if needed).
- Expose REST APIs for the authenticated user to read/update their preferences.
- Extend matchmaking logic to use preferences in addition to countryCode.
- Update documentation (backend-api.md + OpenAPI) to describe new fields and endpoints.

## B. Text messaging & detailed chat history [DONE]

- Status: Implemented (backend & docs).
- Introduce a Message model linked to ChatSession.
- Support sending/receiving text messages in sessions (WebSocket and/or REST).
- Persist messages and expose per-session history endpoints.
- Ensure proper authorization (only session participants + admin).

## C. Notifications v1 [DONE]

- Status: Implemented (backend & docs).
- Define a simple notification model and storage.
- Send notifications for key events (ban/unban, report processed, etc.).
- Expose in-app notifications via WebSocket and REST.
- Optionally support notification preferences per user.

## D. Internationalization & translation [PENDING]

- Status: Planned for later (not started).
- Store user language preferences.
- Optionally integrate with an external translation API for text messages.
- Design endpoints or behaviors to request translated content.

## E. Fun video / AR features (long-term) [PENDING]

- Status: Long-term / experimental (frontend-heavy).
- Mostly frontend-driven (filters, AR effects).
- Backend may store feature flags / user preferences related to visual effects.

## F. Safe reputation & feedback system [DONE]

- Status: Implemented (backend & docs).
- Capture per-session feedback (like/dislike, optional rating, reports) between users.
- Maintain a simple reputation / strikes model per user (safe users vs. at-risk users).
- Integrate reputation with existing reports and bans.
- Use reputation in matchmaking to reduce exposure to toxic users.

## G. Matchmaking modes / rooms [DONE]

- Status: Implemented (backend & docs).
- Allow the user to choose a meeting "mode" (random, English practice, gaming, coworking, etc.).
- Pass the selected mode to the backend during the match search.
- Adapt matchmaking to prioritize matches within the same mode.

## H. Icebreakers & prompts [DONE]

- Status: Implemented (backend & docs).
- Maintain a bank of icebreakers (questions / challenges), potentially by mode and language.
- Send an icebreaker at the start of the session via WebSocket to help start the conversation.
- Optionally expose a REST endpoint to fetch other questions on the frontend side.

## I. Friends & mutual matches [DONE]

- Status: Implemented (backend & docs).
- Record "like" type feedback between users at the end of a session.
- Create a friendship / contact link when there is a mutual match.
- Expose endpoints to list friends/contacts and, later, manage deletion.
- Support direct calls (video / chat) between friends by reusing the existing signaling infrastructure.

## J. Friend direct messages (DM) [DONE]

- Status: Implemented (backend & docs).
- Introduced a `DirectMessage` model for persistent 1-to-1 conversations between friends.
- Exposed REST endpoints to list and send messages between two friends.
- Added a dedicated WebSocket channel to receive new DM messages in real time.

## K. Friend video calls [DONE]

- Status: Implemented (backend signaling & docs).
- Allow starting a direct video call from the friends list / DM view between two friend IDs.
- Reuse existing WebRTC signaling (`/app/signal` and `/user/queue/signal`) between two friend IDs.
- Added dedicated call control signals (`CALL_INVITE`, `CALL_ACCEPT`, `CALL_REJECT`, `CALL_END`) for friend calls.

Oui, on pex ajouter une notification incomming call mais quand le call sera manque, il devient une missing call dans la cloche.

j'ai une question est ce que le call v  1-1 pourrais se passer si le receveur du call est entrain de faire un autre appel en apelle aleatoire de l'app ?