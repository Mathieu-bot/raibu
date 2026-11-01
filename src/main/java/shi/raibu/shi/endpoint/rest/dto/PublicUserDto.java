package shi.raibu.shi.endpoint.rest.dto;

import shi.raibu.shi.model.User;

/** Public view of a user: never expose e-mail, provider ids or moderation data. */
public record PublicUserDto(String id, String displayName, String avatarUrl, String countryCode) {

  public static PublicUserDto from(User user) {
    return new PublicUserDto(
        user.getId(), user.getDisplayName(), user.getAvatarUrl(), user.getCountryCode());
  }
}
