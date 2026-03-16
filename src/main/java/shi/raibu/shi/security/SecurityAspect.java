package shi.raibu.shi.security;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import shi.raibu.shi.exception.ForbiddenException;

@Aspect
@Component
@AllArgsConstructor
@Slf4j
public class SecurityAspect {

  private final RoleService roleService;

  @Around("@annotation(requireRole)")
  public Object checkRole(ProceedingJoinPoint joinPoint, RequireRole requireRole) throws Throwable {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser)) {
      log.warn("Unauthorized access attempt to protected endpoint");
      throw new ForbiddenException("Authentication required");
    }

    OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
    String email = oidcUser.getEmail();

    // Find user by email
    String userId = oidcUser.getSubject();

    if (!roleService.hasRole(userId, requireRole.value())) {
      log.warn(
          "User {} with email {} attempted to access endpoint requiring role {}",
          userId,
          email,
          requireRole.value());
      throw new ForbiddenException("Insufficient permissions");
    }

    log.debug("User {} with email {} authorized for role {}", userId, email, requireRole.value());

    return joinPoint.proceed();
  }
}
