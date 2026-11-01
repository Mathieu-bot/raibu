package shi.raibu.shi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

class CustomOidcUserServiceTest {

  private UserRepository userRepository;
  private OAuth2UserService<OidcUserRequest, OidcUser> delegate;
  private CustomOidcUserService service;
  private OidcUserRequest request;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    userRepository = mock(UserRepository.class);
    delegate = mock(OAuth2UserService.class);
    service = new CustomOidcUserService(userRepository, delegate);
    request = mock(OidcUserRequest.class);
    ClientRegistration registration = mock(ClientRegistration.class);
    when(request.getClientRegistration()).thenReturn(registration);
    when(registration.getRegistrationId()).thenReturn("google");
  }

  private OidcUser googleUser(String email, Object emailVerified, String locale) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("sub", "google-sub-1");
    claims.put("name", "Joe");
    if (email != null) {
      claims.put("email", email);
    }
    if (emailVerified != null) {
      claims.put("email_verified", emailVerified);
    }
    if (locale != null) {
      claims.put("locale", locale);
    }
    OidcIdToken token = new OidcIdToken("t", Instant.now(), Instant.now().plusSeconds(60), claims);
    return new DefaultOidcUser(AuthorityUtils.createAuthorityList("ROLE_USER"), token);
  }

  @Test
  void rejectsUnverifiedEmail() {
    when(delegate.loadUser(request)).thenReturn(googleUser("a@example.com", false, null));

    assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
    verify(userRepository, never()).save(any());
  }

  @Test
  void rejectsMissingVerificationClaim() {
    when(delegate.loadUser(request)).thenReturn(googleUser("a@example.com", null, null));

    assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
    verify(userRepository, never()).save(any());
  }

  @Test
  void rejectsMissingEmail() {
    when(delegate.loadUser(request)).thenReturn(googleUser(null, true, null));

    assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(request));
    verify(userRepository, never()).save(any());
  }

  @Test
  void keepsTheCountryChosenByTheUser() {
    when(delegate.loadUser(request)).thenReturn(googleUser("a@example.com", true, "fr-FR"));
    User existing = User.builder().id("app-uuid").countryCode("MG").build();
    when(userRepository.findByProviderAndProviderId("google", "google-sub-1"))
        .thenReturn(Optional.of(existing));

    service.loadUser(request);

    assertEquals("MG", existing.getCountryCode());
  }

  @Test
  void fillsTheCountryFromTheLocaleWhenItIsUnset() {
    when(delegate.loadUser(request)).thenReturn(googleUser("a@example.com", true, "fr-FR"));
    User existing = User.builder().id("app-uuid").build();
    when(userRepository.findByProviderAndProviderId("google", "google-sub-1"))
        .thenReturn(Optional.of(existing));

    service.loadUser(request);

    assertEquals("FR", existing.getCountryCode());
  }

  @Test
  void acceptsVerifiedEmailAndExposesTheAppUserId() {
    when(delegate.loadUser(request)).thenReturn(googleUser("a@example.com", true, "fr-FR"));
    when(userRepository.findByProviderAndProviderId("google", "google-sub-1"))
        .thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            i -> {
              User u = i.getArgument(0);
              u.setId("app-uuid");
              return u;
            });

    OidcUser result = service.loadUser(request);

    assertEquals("app-uuid", result.getName());
  }
}
