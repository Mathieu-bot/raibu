package shi.raibu.shi.endpoint.rest.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
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
            user.getGender(),
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
      String gender,
      boolean banned,
      User.UserStatus status) {}

  @GetMapping("/me/preferences")
  public ResponseEntity<MePreferencesResponse> getPreferences(
      @AuthenticationPrincipal OidcUser oidcUser) {
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

    MePreferencesResponse response =
        new MePreferencesResponse(
            user.getCountryCode(),
            splitCsv(user.getPreferredLanguages()),
            splitCsv(user.getInterests()),
            splitCsv(user.getPreferredGenders()),
            user.getPreferSameCountry());

    return ResponseEntity.ok(response);
  }

  @PutMapping("/me/preferences")
  public ResponseEntity<Void> updatePreferences(
      @AuthenticationPrincipal OidcUser oidcUser, @RequestBody UpdatePreferencesRequest request) {
    if (oidcUser == null) {
      return ResponseEntity.status(401).build();
    }

    if (request == null) {
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

    if (request.preferredLanguages() != null) {
      user.setPreferredLanguages(joinCsv(request.preferredLanguages()));
    }
    if (request.interests() != null) {
      user.setInterests(joinCsv(request.interests()));
    }
    if (request.preferredGenders() != null) {
      user.setPreferredGenders(joinCsv(request.preferredGenders()));
    }
    if (request.preferSameCountry() != null) {
      user.setPreferSameCountry(request.preferSameCountry());
    }

    if (request.gender() != null && !request.gender().isBlank()) {
      user.setGender(request.gender().trim());
    }

    userRepository.save(user);

    return ResponseEntity.ok().build();
  }

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

  public record MePreferencesResponse(
      String countryCode,
      List<String> preferredLanguages,
      List<String> interests,
      List<String> preferredGenders,
      Boolean preferSameCountry) {}

  public record UpdatePreferencesRequest(
      List<String> preferredLanguages,
      List<String> interests,
      List<String> preferredGenders,
      Boolean preferSameCountry,
      String gender) {}

  private static String joinCsv(List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.stream()
        .map(String::trim)
        .filter(v -> !v.isEmpty())
        .collect(Collectors.joining(","));
  }

  private static List<String> splitCsv(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    String[] parts = value.split(",");
    List<String> result = new ArrayList<>();
    for (String part : parts) {
      String trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }
}
