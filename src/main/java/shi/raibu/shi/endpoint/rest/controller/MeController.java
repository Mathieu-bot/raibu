package shi.raibu.shi.endpoint.rest.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@RestController
@AllArgsConstructor
public class MeController {

  private final UserRepository userRepository;

  @GetMapping("/me")
  public ResponseEntity<MeResponse> getMe(@AuthenticationPrincipal OidcUser oidcUser) {
    if (oidcUser == null) {
      return ResponseEntity.status(401).build();
    }

    String subject = oidcUser.getSubject();
    String email = oidcUser.getEmail();

    User user =
        userRepository
            .findByProviderAndProviderId("google", subject)
            .orElseGet(() -> userRepository.findByEmail(email).orElse(null));

    if (user == null) {
      return ResponseEntity.notFound().build();
    }

    MeResponse response =
        new MeResponse(
            user.getId(),
            user.getDisplayName(),
            user.getEmail(),
            user.getAvatarUrl(),
            user.isBanned(),
            user.getStatus());

    return ResponseEntity.ok(response);
  }

  public record MeResponse(
      String id,
      String displayName,
      String email,
      String avatarUrl,
      boolean banned,
      User.UserStatus status) {}
}
