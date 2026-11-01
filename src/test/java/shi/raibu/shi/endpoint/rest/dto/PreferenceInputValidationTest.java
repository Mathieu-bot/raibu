package shi.raibu.shi.endpoint.rest.dto;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceInputValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void acceptsTenShortLanguages() {
    UserPreferencesDto dto = new UserPreferencesDto();
    dto.setPreferredLanguages(Collections.nCopies(10, "fr"));
    assertTrue(validator.validate(dto).isEmpty());
  }

  @Test
  void rejectsMoreThanTenItemsInAnyList() {
    List<String> eleven = Collections.nCopies(11, "x");
    UserPreferencesDto dto = new UserPreferencesDto();
    dto.setPreferredLanguages(eleven);
    dto.setInterests(eleven);
    dto.setPreferredGenders(eleven);
    assertEquals(3, validator.validate(dto).size());
  }

  @Test
  void rejectsTooLongItems() {
    UserPreferencesDto dto = new UserPreferencesDto();
    dto.setPreferredLanguages(List.of("x".repeat(16)));
    dto.setInterests(List.of("x".repeat(51)));
    assertEquals(2, validator.validate(dto).size());
  }

  @Test
  void countryMustBeTwoLetters() {
    UpdateCountryDto ok = new UpdateCountryDto();
    ok.setCountryCode("fr");
    assertTrue(validator.validate(ok).isEmpty());
    for (String bad : List.of("F", "FRA", "1A", "F ", "")) {
      UpdateCountryDto dto = new UpdateCountryDto();
      dto.setCountryCode(bad);
      assertFalse(validator.validate(dto).isEmpty(), "should reject '" + bad + "'");
    }
  }
}
