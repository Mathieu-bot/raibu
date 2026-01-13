# Raibu Backend TODOs

## A. Preferences & Matchmaking v2

- Implement advanced user preferences (interests, languages, finer location if needed).
- Expose REST APIs for the authenticated user to read/update their preferences.
- Extend matchmaking logic to use preferences in addition to countryCode.
- Update documentation (backend-api.md + OpenAPI) to describe new fields and endpoints.

## B. Text messaging & detailed chat history

- Introduce a Message model linked to ChatSession.
- Support sending/receiving text messages in sessions (WebSocket and/or REST).
- Persist messages and expose per-session history endpoints.
- Ensure proper authorization (only session participants + admin).

## C. Notifications v1

- Define a simple notification model and storage.
- Send notifications for key events (ban/unban, report processed, etc.).
- Expose in-app notifications via WebSocket and REST.
- Optionally support notification preferences per user.

## D. Internationalization & translation

- Store user language preferences.
- Optionally integrate with an external translation API for text messages.
- Design endpoints or behaviors to request translated content.

## E. Fun video / AR features (long-term)

- Mostly frontend-driven (filters, AR effects).
- Backend may store feature flags / user preferences related to visual effects.
