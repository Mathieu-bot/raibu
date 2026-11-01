package shi.raibu.shi.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.Role;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminSecurityTest {

  @Mock private UserRepository userRepository;

  private RoleService roleService;

  @BeforeEach
  void setUp() {
    roleService = new RoleService(userRepository, "admin@raibu.app, Mathieu@Raibu.app ,,");
  }

  @Nested
  @DisplayName("Role checking")
  class RoleCheckingTests {

    @Test
    @DisplayName("should identify admin users by email")
    void shouldIdentifyAdminUsersByEmail() {
      User adminUser = User.builder().id("admin-1").email("admin@raibu.app").build();

      when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

      assertTrue(roleService.isAdmin("admin-1"));
      assertTrue(roleService.hasRole("admin-1", Role.ADMIN));
    }

    @Test
    @DisplayName("should identify regular users")
    void shouldIdentifyRegularUsers() {
      User regularUser = User.builder().id("user-1").email("user@gmail.com").build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(regularUser));

      assertFalse(roleService.isAdmin("user-1"));
      assertFalse(roleService.hasRole("user-1", Role.ADMIN));
      assertTrue(roleService.hasRole("user-1", Role.USER));
    }

    @Test
    @DisplayName("should return USER role for non-existent users")
    void shouldReturnUserRoleForNonExistentUsers() {
      when(userRepository.findById("non-existent")).thenReturn(Optional.empty());

      assertEquals(Role.USER, roleService.getUserRole("non-existent"));
      assertFalse(roleService.hasRole("non-existent", Role.ADMIN));
    }
  }

  @Nested
  @DisplayName("Role hierarchy")
  class RoleHierarchyTests {

    @Test
    @DisplayName("should allow admin access to moderator endpoints")
    void shouldAllowAdminAccessToModeratorEndpoints() {
      User adminUser = User.builder().id("admin-1").email("admin@raibu.app").build();

      when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

      assertTrue(roleService.hasRole("admin-1", Role.MODERATOR));
      assertTrue(roleService.hasRole("admin-1", Role.USER));
    }

    @Test
    @DisplayName("should allow user access to user endpoints")
    void shouldAllowUserAccessToUserEndpoints() {
      User regularUser = User.builder().id("user-1").email("user@gmail.com").build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(regularUser));

      assertTrue(roleService.hasRole("user-1", Role.USER));
    }
  }

  @Nested
  @DisplayName("ADMIN_EMAILS parsing")
  class AdminEmailsTests {

    private boolean adminWith(String config, String email) {
      RoleService service = new RoleService(userRepository, config);
      when(userRepository.findById("u"))
          .thenReturn(Optional.of(User.builder().id("u").email(email).build()));
      return service.isAdmin("u");
    }

    @Test
    void emptyConfigurationMeansNobodyIsAdmin() {
      assertFalse(adminWith("", "admin@raibu.app"));
      assertFalse(adminWith(null, "admin@raibu.app"));
      assertFalse(adminWith(" , ", "admin@raibu.app"));
    }

    @Test
    void singleEmailIsAdmin() {
      assertTrue(adminWith("boss@example.com", "boss@example.com"));
      assertFalse(adminWith("boss@example.com", "other@example.com"));
    }

    @Test
    void severalEmailsWithSpacesAndMixedCase() {
      String config = " Boss@Example.com , second@example.com,, THIRD@example.com ";
      assertTrue(adminWith(config, "boss@example.com"));
      assertTrue(adminWith(config, "Second@Example.com"));
      assertTrue(adminWith(config, "third@example.com"));
      assertFalse(adminWith(config, "fourth@example.com"));
    }

    @Test
    void noEmailIsNeverAdmin() {
      assertFalse(adminWith("boss@example.com", null));
    }
  }
}
