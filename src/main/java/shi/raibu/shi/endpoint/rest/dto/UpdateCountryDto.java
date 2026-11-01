package shi.raibu.shi.endpoint.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class UpdateCountryDto {

  @NotBlank(message = "Country code is required")
  @Pattern(regexp = "^[A-Za-z]{2}$", message = "Country code must be exactly 2 letters")
  private String countryCode;

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }
}
