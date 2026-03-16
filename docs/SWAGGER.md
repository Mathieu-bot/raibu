# API Documentation with Swagger/OpenAPI

## Overview

Raibu backend now includes interactive API documentation powered by SpringDoc OpenAPI.

## Accessing the Documentation

### Local Development
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`
- **OpenAPI YAML**: `http://localhost:8080/v3/api-docs.yaml`

### Production
- **Swagger UI**: `https://api.raibu.app/swagger-ui/index.html`
- **OpenAPI JSON**: `https://api.raibu.app/v3/api-docs`

## Features

### Interactive API Testing
- **Try it out** button for each endpoint
- Automatic authentication handling (OAuth2)
- Request/response examples
- Schema validation

### API Organization
Endpoints are organized by tags:
- **User Profile**: User profile and preferences management
- **Reports**: User reporting and moderation
- **Health**: Health check and monitoring endpoints

### Authentication
Most endpoints require OAuth2 authentication. In Swagger UI:
1. Click the "Authorize" button (lock icon)
2. Select OAuth2.0 configuration
3. Enter your Google OAuth credentials
4. Click "Authorize" and login via Google

## API Documentation Standards

### Controller Annotations
Each endpoint includes:
- `@Operation`: Summary and detailed description
- `@ApiResponses`: Documented response codes
- `@Tag`: Logical grouping
- `@SecurityRequirement`: Authentication requirements

### DTO Documentation
- `@Schema`: Field descriptions and validation rules
- Automatic type inference from Java classes
- Enum value documentation

## Examples

### Example Endpoint Documentation
```java
@Operation(summary = "Get current user profile",
    description = "Returns the profile information of the authenticated user")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "User profile retrieved successfully"),
    @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
    @ApiResponse(responseCode = "404", description = "User not found")
})
@GetMapping("/me")
public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal OidcUser oidcUser) {
    // implementation
}
```

## Configuration

OpenAPI configuration is in `OpenApiConfig.java`:
- API version: `1.0.0`
- Servers: Local (http://localhost:8080) and Production (https://api.raibu.app)
- Contact: Raibu Team (contact@raibu.app)

## Best Practices

1. **Keep documentation synchronized with code**
2. **Use clear, concise descriptions**
3. **Document all response codes**
4. **Include request/response examples for complex DTOs**
5. **Update documentation when adding new endpoints**
