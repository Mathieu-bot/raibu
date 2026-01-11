package shi.raibu.shi.security;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final UserRepository userRepository;
  private final OidcUserService delegate = new OidcUserService();

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    OidcUser oidcUser = delegate.loadUser(userRequest);

    String provider = userRequest.getClientRegistration().getRegistrationId(); // "google"
    String providerId = oidcUser.getSubject();
    String email = oidcUser.getEmail();
    String name = oidcUser.getFullName();
    String picture = oidcUser.getPicture();

    User user =
        userRepository
            .findByProviderAndProviderId(provider, providerId)
            .orElseGet(
                () ->
                    User.builder()
                        .provider(provider)
                        .providerId(providerId)
                        .email(email)
                        .displayName(name)
                        .avatarUrl(picture)
                        .status(User.UserStatus.IDLE)
                        .createdAt(Instant.now())
                        .lastActiveAt(Instant.now())
                        .banned(false)
                        .build());

    user.setEmail(email);
    user.setDisplayName(name);
    user.setAvatarUrl(picture);
    user.setLastActiveAt(Instant.now());

    userRepository.save(user);

    return oidcUser;
  }
}
