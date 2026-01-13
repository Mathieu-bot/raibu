package shi.raibu.shi.endpoint.rest.controller;

import java.util.Locale;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
            user.getCountryCode(),
            user.isBanned(),
            user.getStatus());

    return ResponseEntity.ok(response);
  }

  public record MeResponse(
      String id,
      String displayName,
      String email,
      String avatarUrl,
      String countryCode,
      boolean banned,
      User.UserStatus status) {}

  @PutMapping("/me/country")
  public ResponseEntity<Void> updateCountry(
      @AuthenticationPrincipal OidcUser oidcUser, @RequestBody UpdateCountryRequest request) {
    if (oidcUser == null) {
      return ResponseEntity.status(401).build();
    }

    if (request == null || request.countryCode == null || request.countryCode().isBlank()) {
      return ResponseEntity.badRequest().build();
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

    String normalizedCountry = request.countryCode().trim().toUpperCase(Locale.ROOT);
    user.setCountryCode(normalizedCountry);
    userRepository.save(user);

    return ResponseEntity.ok().build();
  }

  public record UpdateCountryRequest(String countryCode) {}
}
