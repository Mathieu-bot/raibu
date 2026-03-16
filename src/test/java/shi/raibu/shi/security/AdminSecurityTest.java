package shi.raibu.shi.security;

import static org.junit.jupiter.api.Assertions.*;

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

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminSecurityTest {

  @Mock
  private UserRepository userRepository;

  private RoleService roleService;

  @BeforeEach
  void setUp() {
    roleService = new RoleService(userRepository);
  }

  @Nested
  @DisplayName("Role checking")
  class RoleCheckingTests {

    @Test
    @DisplayName("should identify admin users by email")
    void shouldIdentifyAdminUsersByEmail() {
      User adminUser = User.builder()
          .id("admin-1")
          .email("admin@raibu.app")
          .build();

      when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

      assertTrue(roleService.isAdmin("admin-1"));
      assertTrue(roleService.hasRole("admin-1", Role.ADMIN));
    }

    @Test
    @DisplayName("should identify regular users")
    void shouldIdentifyRegularUsers() {
      User regularUser = User.builder()
          .id("user-1")
          .email("user@gmail.com")
          .build();

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
      User adminUser = User.builder()
          .id("admin-1")
          .email("admin@raibu.app")
          .build();

      when(userRepository.findById("admin-1")).thenReturn(Optional.of(adminUser));

      assertTrue(roleService.hasRole("admin-1", Role.MODERATOR));
      assertTrue(roleService.hasRole("admin-1", Role.USER));
    }

    @Test
    @DisplayName("should allow user access to user endpoints")
    void shouldAllowUserAccessToUserEndpoints() {
      User regularUser = User.builder()
          .id("user-1")
          .email("user@gmail.com")
          .build();

      when(userRepository.findById("user-1")).thenReturn(Optional.of(regularUser));

      assertTrue(roleService.hasRole("user-1", Role.USER));
    }
  }

  @Nested
  @DisplayName("Default admin configuration")
  class DefaultAdminConfigurationTests {

    @Test
    @DisplayName("should include mathieu@raibu.app as default admin")
    void shouldIncludeMathieuAsDefaultAdmin() {
      User mathieuUser = User.builder()
          .id("mathieu-1")
          .email("mathieu@raibu.app")
          .build();

      when(userRepository.findById("mathieu-1")).thenReturn(Optional.of(mathieuUser));

      assertTrue(roleService.isAdmin("mathieu-1"));
    }

    @Test
    @DisplayName("should provide list of admin emails")
    void shouldProvideListOfAdminEmails() {
      var adminEmails = roleService.getAdminEmails();

      assertTrue(adminEmails.contains("admin@raibu.app"));
      assertTrue(adminEmails.contains("mathieu@raibu.app"));
      assertEquals(2, adminEmails.size());
    }
  }
}
