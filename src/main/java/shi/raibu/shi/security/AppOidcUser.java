package shi.raibu.shi.security;

import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class AppOidcUser extends DefaultOidcUser {

  private final String appUserId;

  public AppOidcUser(String appUserId, OidcUser delegate) {
    super(delegate.getAuthorities(), delegate.getIdToken(), delegate.getUserInfo());
    this.appUserId = appUserId;
  }

  @Override
  public String getName() {
    return appUserId;
  }
}
