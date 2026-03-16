# Admin Security Documentation

## Overview

Raibu backend implements a role-based access control (RBAC) system to protect administrative endpoints from unauthorized access.

## Role System

### Available Roles
```java
public enum Role {
  USER,       // Regular user
  MODERATOR,  // Content moderator
  ADMIN       // Full administrator
}
```

### Role Hierarchy
Roles are hierarchical with the following precedence:
```
ADMIN > MODERATOR > USER
```

Higher roles include all permissions of lower roles.

## Configuration

### Default Admins
Default administrator emails are configured in `RoleService.java`:

```java
private static final Set<String> DEFAULT_ADMINS = Set.of(
    "admin@raibu.app",
    "mathieu@raibu.app"
);
```

**⚠️ Important**: Move these to environment variables in production!

### Environment Variable Configuration (Future)
```properties
# Application properties
raibu.admin.emails=admin@raibu.app,moderator@raibu.app
raibu.moderator.emails=moderator1@raibu.app,moderator2@raibu.app
```

## Usage

### Protecting Admin Endpoints

#### Method-Level Security
```java
@RestController
@RequestMapping("/admin")
public class AdminModerationController {

  @GetMapping("/reports")
  @RequireRole(Role.ADMIN)
  public ResponseEntity<List<Report>> getReports() {
      // Only accessible by ADMIN users
  }
}
```

#### Available Annotations
```java
@RequireRole(Role.ADMIN)      // Admin only
@RequireRole(Role.MODERATOR)  // Moderators and Admins
@RequireRole(Role.USER)       // All authenticated users
```

## Security Components

### 1. Role Enum
Defines the available roles in the system.

### 2. @RequireRole Annotation
Method-level annotation to specify required role:
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {
  Role value();
}
```

### 3. RoleService
Service for role checking and user role determination:
```java
public boolean hasRole(String userId, Role requiredRole)
public Role getUserRole(String userId)
public boolean isAdmin(String userId)
public boolean isModerator(String userId)
```

### 4. SecurityAspect
AOP aspect that intercepts methods annotated with `@RequireRole`:
- Checks user authentication
- Verifies user has required role
- Throws `ForbiddenException` if unauthorized

## Protected Endpoints

### Current Admin Endpoints
All endpoints in `AdminModerationController` are protected:

```
GET    /admin/reports              - List all reports
GET    /admin/reports?status=PENDING - Filter reports by status
GET    /admin/users/{userId}/reports - Get reports for specific user
PUT    /admin/reports/{reportId}/status - Update report status
GET    /admin/users/{userId}/sessions - Get user sessions
PUT    /admin/users/{userId}/ban      - Ban/unban user
```

### Future Protected Endpoints
Consider protecting additional endpoints:
```
POST   /admin/users/{userId}/strike    - Add strike to user
GET    /admin/analytics                - System analytics
DELETE /admin/users/{userId}           - Delete user
POST   /admin/announce                 - System announcements
```

## Error Handling

### Authentication Required
When unauthenticated users attempt to access protected endpoints:
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Authentication required"
}
```

### Insufficient Permissions
When authenticated users lack required role:
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Insufficient permissions"
}
```

## Security Best Practices

### 1. Environment-Based Configuration
Move admin emails to environment variables:
```java
@Value("${raibu.admin.emails:}")
private String adminEmailsConfig;

private Set<String> getAdminEmails() {
    return Arrays.stream(adminEmailsConfig.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
}
```

### 2. Database-Backed Roles
For better scalability, store roles in database:
```java
@Entity
public class UserRole {
    @Id
    private String userId;
    @Enumerated(EnumType.STRING)
    private Role role;
    private Instant createdAt;
}
```

### 3. Audit Logging
Log all admin actions for security auditing:
```java
@Around("@annotation(requireRole)")
public Object auditAdminAction(ProceedingJoinPoint joinPoint, RequireRole requireRole) {
    String method = joinPoint.getSignature().getName();
    String userId = getCurrentUserId();

    log.info("Admin action: {} by user: {}", method, userId);

    try {
        Object result = joinPoint.proceed();
        log.info("Admin action completed: {} by user: {}", method, userId);
        return result;
    } catch (Throwable e) {
        log.error("Admin action failed: {} by user: {}", method, userId, e);
        throw e;
    }
}
```

### 4. Rate Limiting
Implement stricter rate limiting for admin endpoints:
```java
@RequireRole(Role.ADMIN)
@RateLimit(perMinute = 30)  // Stricter than regular endpoints
public ResponseEntity<List<Report>> getReports() {
    // ...
}
```

## Testing

### Testing Role-Based Access
```java
@SpringBootTest
@AutoConfigureMockMvc
class AdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void whenUserAccessesAdminEndpoint_thenForbidden() throws Exception {
        mockMvc.perform(get("/admin/reports"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void whenAdminAccessesAdminEndpoint_thenAllowed() throws Exception {
        mockMvc.perform(get("/admin/reports"))
            .andExpect(status().isOk());
    }
}
```

### Testing RoleService
```java
@Test
void whenUserIsAdmin_thenReturnsTrue() {
    assertTrue(roleService.hasRole("admin-user-id", Role.ADMIN));
}

@Test
void whenUserIsRegularUser_thenCannotAccessAdminEndpoints() {
    assertFalse(roleService.hasRole("regular-user-id", Role.ADMIN));
}
```

## Deployment Considerations

### Development
- Use default admin emails for testing
- Enable detailed security logging
- Test role enforcement

### Staging
- Mirror production admin configuration
- Test admin workflows
- Verify security policies

### Production
- Use environment-specific admin emails
- Enable audit logging
- Monitor for unauthorized access attempts
- Regular security audits

## Troubleshooting

### Common Issues

#### 1. "Insufficient permissions" error
**Cause**: User email not in admin list
**Solution**: Add user email to `DEFAULT_ADMINS` or environment variable

#### 2. "Authentication required" error
**Cause**: User not authenticated via OAuth2
**Solution**: Ensure proper OAuth2 login flow

#### 3. Security aspect not working
**Cause**: Missing `@EnableAspectJAutoProxy` annotation
**Solution**: Add to main application class:
```java
@SpringBootApplication
@EnableAspectJAutoProxy
public class PojaApplication {
    // ...
}
```

## Migration from Old System

### Before
All endpoints were protected only by OAuth2 authentication:
```java
@GetMapping("/admin/reports")
public ResponseEntity<List<Report>> getReports() {
    // Anyone with OAuth2 could access
}
```

### After
Endpoints are protected by role-based access:
```java
@GetMapping("/admin/reports")
@RequireRole(Role.ADMIN)
public ResponseEntity<List<Report>> getReports() {
    // Only ADMIN role can access
}
```

## Future Enhancements

1. **Fine-grained permissions**: Permission-based access control in addition to roles
2. **Temporary admin access**: Time-limited admin privileges
3. **Multi-factor authentication**: Additional security for admin operations
4. **IP whitelisting**: Restrict admin access to specific IPs
5. **Session-based admin**: Require re-authentication for sensitive operations
