package shi.raibu.shi.endpoint.rest.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import shi.raibu.shi.endpoint.rest.dto.UpdateCountryDto;
import shi.raibu.shi.endpoint.rest.dto.UserPreferencesDto;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@RestController
@AllArgsConstructor
@Tag(name = "User Profile", description = "Endpoints for managing current user profile and preferences")
@SecurityRequirement(name = "oauth2")
public class MeController {

  private final UserRepository userRepository;

  @GetMapping("/me")
  @Operation(summary = "Get current user profile", description = "Returns the profile information of the authenticated user")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "User profile retrieved successfully",
          content = @Content(schema = @Schema(implementation = MeResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
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
  @Operation(summary = "Get user preferences", description = "Returns the matchmaking-related preferences for the authenticated user")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Preferences retrieved successfully",
          content = @Content(schema = @Schema(implementation = MePreferencesResponse.class))),
      @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
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
  @Operation(summary = "Update user preferences", description = "Updates the matchmaking-related preferences for the authenticated user. All fields are optional.")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Preferences updated successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid request data"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  public ResponseEntity<Void> updatePreferences(
      @AuthenticationPrincipal OidcUser oidcUser, @Valid @RequestBody UserPreferencesDto request) {
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

    if (request.getPreferredLanguages() != null) {
      user.setPreferredLanguages(joinCsv(request.getPreferredLanguages()));
    }
    if (request.getInterests() != null) {
      user.setInterests(joinCsv(request.getInterests()));
    }
    if (request.getPreferredGenders() != null) {
      user.setPreferredGenders(joinCsv(request.getPreferredGenders()));
    }
    if (request.getPreferSameCountry() != null) {
      user.setPreferSameCountry(request.getPreferSameCountry());
    }

    if (request.getGender() != null && !request.getGender().isBlank()) {
      user.setGender(request.getGender().trim());
    }

    userRepository.save(user);

    return ResponseEntity.ok().build();
  }

  @PutMapping("/me/country")
  @Operation(summary = "Update user country", description = "Allows the authenticated user to update their preferred country code")
  @ApiResponses(value = {
      @ApiResponse(responseCode = "200", description = "Country updated successfully"),
      @ApiResponse(responseCode = "400", description = "Invalid country code"),
      @ApiResponse(responseCode = "401", description = "Unauthorized - user not authenticated"),
      @ApiResponse(responseCode = "404", description = "User not found")
  })
  public ResponseEntity<Void> updateCountry(
      @AuthenticationPrincipal OidcUser oidcUser, @Valid @RequestBody UpdateCountryDto request) {
    if (oidcUser == null) {
      return ResponseEntity.status(401).build();
    }

    if (request == null || request.getCountryCode() == null || request.getCountryCode().isBlank()) {
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

    String normalizedCountry = request.getCountryCode().trim().toUpperCase(Locale.ROOT);
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
