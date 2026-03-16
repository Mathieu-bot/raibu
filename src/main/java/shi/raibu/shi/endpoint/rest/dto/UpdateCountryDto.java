package shi.raibu.shi.endpoint.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateCountryDto {

  @NotBlank(message = "Country code is required")
  @Size(min = 2, max = 2, message = "Country code must be exactly 2 characters")
  private String countryCode;

  public String getCountryCode() {
    return countryCode;
  }

  public void setCountryCode(String countryCode) {
    this.countryCode = countryCode;
  }
}
