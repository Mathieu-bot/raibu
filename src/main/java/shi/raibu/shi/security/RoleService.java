package shi.raibu.shi.security;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.Role;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

import java.util.Set;

@Service
@AllArgsConstructor
@Slf4j
public class RoleService {

  private final UserRepository userRepository;

  // Default admin emails (should be moved to configuration)
  private static final Set<String> DEFAULT_ADMINS = Set.of(
      "admin@raibu.app",
      "mathieu@raibu.app"
  );

  public Role getUserRole(String userId) {
    return userRepository.findById(userId)
        .map(this::determineRole)
        .orElse(Role.USER);
  }

  public boolean hasRole(String userId, Role requiredRole) {
    Role userRole = getUserRole(userId);
    return userRole.ordinal() >= requiredRole.ordinal();
  }

  public boolean isAdmin(String userId) {
    return hasRole(userId, Role.ADMIN);
  }

  public boolean isModerator(String userId) {
    return hasRole(userId, Role.MODERATOR);
  }

  private Role determineRole(User user) {
    // Check if user is a default admin by email
    if (DEFAULT_ADMINS.contains(user.getEmail())) {
      return Role.ADMIN;
    }

    // Additional role logic can be added here
    // For example, checking a user_roles table or flags in the User model

    return Role.USER;
  }

  public Set<String> getAdminEmails() {
    return DEFAULT_ADMINS;
  }
}
