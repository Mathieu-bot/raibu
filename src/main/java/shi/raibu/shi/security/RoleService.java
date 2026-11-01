package shi.raibu.shi.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.Role;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@Service
@Slf4j
public class RoleService {

  private final UserRepository userRepository;

  /** Lower-cased admin e-mails parsed from the comma separated ADMIN_EMAILS value. */
  private final Set<String> adminEmails;

  public RoleService(UserRepository userRepository, @Value("${ADMIN_EMAILS:}") String adminEmails) {
    this.userRepository = userRepository;
    this.adminEmails = parseEmails(adminEmails);
  }

  static Set<String> parseEmails(String raw) {
    if (raw == null || raw.isBlank()) {
      return Set.of();
    }
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(e -> !e.isEmpty())
        .map(e -> e.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  public Role getUserRole(String userId) {
    return userRepository.findById(userId).map(this::determineRole).orElse(Role.USER);
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
    if (user.getEmail() != null
        && adminEmails.contains(user.getEmail().trim().toLowerCase(Locale.ROOT))) {
      return Role.ADMIN;
    }

    // Additional role logic can be added here
    // For example, checking a user_roles table or flags in the User model

    return Role.USER;
  }
}
