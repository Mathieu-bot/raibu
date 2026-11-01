# Swagger UI and OpenAPI

The REST controllers are annotated with springdoc (`@Operation`, `@ApiResponses`, `@Tag`); `OpenApiConfig` sets the title and server list.
The authoritative client contract is [`API.md`](API.md) (it also covers the WebSocket part, which OpenAPI cannot describe). Swagger is a
convenience for browsing REST endpoints during development.

## Access

| Environment | Swagger UI | OpenAPI JSON |
|---|---|---|
| Local / preprod without the `prod` profile | `http://localhost:8080/swagger-ui/index.html` | `http://localhost:8080/v3/api-docs` |
| `SPRING_PROFILES_ACTIVE=prod` | disabled | disabled |

`springdoc.swagger-ui.enabled` and `springdoc.api-docs.enabled` are set to `false` in `application-prod.yml`. When enabled, the paths still require a signed-in session
(sign in first through `/oauth2/authorization/google` in the same browser). The "Authorize" button of Swagger UI does not run the Google flow; rely on the session cookie.

`POST`/`PUT`/`DELETE` calls from "Try it out" need the CSRF token like any other client (see `API.md`).

## Keeping it useful

- Add `@Operation`/`@ApiResponses` to new endpoints; update `API.md` in the same pull request.
- `OpenApiConfig` reads `API_LOCAL_URL`, `API_PRODUCTION_URL` and `CONTACT_URL` for the metadata only.
