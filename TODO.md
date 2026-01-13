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

## F. Safe reputation & feedback system 

- Capture per-session feedback (like/dislike, optional rating, reports) between users.
- Maintain a simple reputation / strikes model per user (safe users vs. at-risk users).
- Integrate reputation with existing reports and bans.
- Use reputation in matchmaking to reduce exposure to toxic users.

## G. Matchmaking modes / rooms

- Allow the user to choose a meeting "mode" (random, English practice, gaming, coworking, etc.).
- Pass the selected mode to the backend during the match search.
- Adapt matchmaking to prioritize matches within the same mode.

## H. Icebreakers & prompts

- Maintain a bank of icebreakers (questions / challenges), potentially by mode and language.
- Send an icebreaker at the start of the session via WebSocket to help start the conversation.
- Optionally expose a REST endpoint to fetch other questions on the frontend side.

## I. Friends & mutual matches

- Record "like" type feedback between users at the end of a session.
- Create a friendship / contact link when there is a mutual match.
- Expose endpoints to list friends/contacts and, later, manage deletion.
- Support direct calls (video / chat) between friends by reusing the existing signaling infrastructure.n