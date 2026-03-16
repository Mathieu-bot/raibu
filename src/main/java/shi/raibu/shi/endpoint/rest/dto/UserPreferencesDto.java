package shi.raibu.shi.endpoint.rest.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public class UserPreferencesDto {

  @Size(max = 2, message = "Country code must be 2 characters")
  private String countryCode;

  private List<@Size(max = 50, message = "Language code too long") String> preferredLanguages;

  private List<@Size(max = 50, message = "Interest too long") String> interests;

  private List<@Size(max = 20, message = "Gender preference too long") String> preferredGenders;

  private Boolean preferSameCountry;

  @Size(max = 20, message = "Gender must be less than 20 characters")
  private String gender;

  // Getters and setters
  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }

  public List<String> getPreferredLanguages() {
    return preferredLanguages;
  }

  public void setPreferredLanguages(List<String> preferredLanguages) {
    this.preferredLanguages = preferredLanguages;
  }

  public List<String> getInterests() {
    return interests;
  }

  public void setInterests(List<String> interests) {
    this.interests = interests;
  }

  public List<String> getPreferredGenders() {
    return preferredGenders;
  }

  public void setPreferredGenders(List<String> preferredGenders) {
    this.preferredGenders = preferredGenders;
  }

  public Boolean getPreferSameCountry() {
    return preferSameCountry;
  }

  public void setPreferSameCountry(Boolean preferSameCountry) {
    this.preferSameCountry = preferSameCountry;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }
}
