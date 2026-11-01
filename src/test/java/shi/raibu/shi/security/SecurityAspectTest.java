package shi.raibu.shi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import shi.raibu.shi.exception.ForbiddenException;
import shi.raibu.shi.model.Role;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

/** Exercises {@link RequireRole} through the Spring AOP proxy with an {@link AppOidcUser}. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SecurityAspectTest.TestConfig.class)
class SecurityAspectTest {

  static final String ADMIN_ID = "11111111-1111-1111-1111-111111111111";
  static final String USER_ID = "22222222-2222-2222-2222-222222222222";
  static final String GOOGLE_SUB = "109876543210987654321";

  @Configuration
  @EnableAspectJAutoProxy(proxyTargetClass = true)
  static class TestConfig {
    @Bean
    UserRepository userRepository() {
      UserRepository repo = mock(UserRepository.class);
      when(repo.findById(ADMIN_ID))
          .thenReturn(Optional.of(User.builder().id(ADMIN_ID).email("admin@raibu.app").build()));
      when(repo.findById(USER_ID))
          .thenReturn(Optional.of(User.builder().id(USER_ID).email("joe@example.com").build()));
      return repo;
    }

    @Bean
    RoleService roleService(UserRepository repo) {
      return new RoleService(repo, "admin@raibu.app");
    }

    @Bean
    SecurityAspect securityAspect(RoleService roleService) {
      return new SecurityAspect(roleService);
    }

    @Bean
    ProtectedBean protectedBean() {
      return new ProtectedBean();
    }
  }

  static class ProtectedBean {
    @RequireRole(Role.ADMIN)
    public String adminOnly() {
      return "ok";
    }
  }

  @Autowired private ProtectedBean protectedBean;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  private void login(String appUserId) {
    OidcIdToken token =
        new OidcIdToken(
            "t",
            Instant.now(),
            Instant.now().plusSeconds(60),
            Map.of("sub", GOOGLE_SUB, "email", "x@example.com"));
    AppOidcUser principal =
        new AppOidcUser(
            appUserId, new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), token));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
  }

  @Test
  void adminIsAuthorizedThroughTheProxy() {
    login(ADMIN_ID);
    assertEquals("ok", protectedBean.adminOnly());
  }

  @Test
  void regularUserIsForbiddenThroughTheProxy() {
    login(USER_ID);
    assertThrows(ForbiddenException.class, () -> protectedBean.adminOnly());
  }

  @Test
  void anonymousIsForbidden() {
    assertThrows(ForbiddenException.class, () -> protectedBean.adminOnly());
  }
}
