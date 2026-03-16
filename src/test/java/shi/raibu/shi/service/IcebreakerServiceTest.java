package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class IcebreakerServiceTest {

  private IcebreakerService icebreakerService;

  @BeforeEach
  void setUp() {
    icebreakerService = new IcebreakerService();
  }

  @Nested
  @DisplayName("getRandom()")
  class GetRandomTests {

    @Test
    @DisplayName("should return non-null icebreaker for default mode")
    void shouldReturnNonNullForDefaultMode() {
      String result = icebreakerService.getRandom(null);
      assertNotNull(result);
      assertFalse(result.isBlank());
    }

    @Test
    @DisplayName("should return icebreaker from default list for null mode")
    void shouldReturnFromDefaultListForNullMode() {
      List<String> defaultIcebreakers =
          List.of(
              "What's something small that made you smile recently?",
              "If we could grab a coffee right now, what would you order?",
              "What song always puts you in a good mood?",
              "If you could relive one nice moment from this year, which one would you choose?");

      String result = icebreakerService.getRandom(null);
      assertTrue(defaultIcebreakers.contains(result));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("should return default icebreaker for null or empty mode")
    void shouldReturnDefaultForNullOrEmpty(String mode) {
      String result = icebreakerService.getRandom(mode);
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for english_practice mode")
    void shouldReturnForEnglishPracticeMode() {
      String result = icebreakerService.getRandom("english_practice");
      assertNotNull(result);
      assertTrue(
          result.contains("English")
              || result.contains("practice")
              || result.contains("movie")
              || result.contains("series"));
    }

    @Test
    @DisplayName("should return icebreaker for gaming mode")
    void shouldReturnForGamingMode() {
      String result = icebreakerService.getRandom("gaming");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for coworking mode")
    void shouldReturnForCoworkingMode() {
      String result = icebreakerService.getRandom("coworking");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for travel mode")
    void shouldReturnForTravelMode() {
      String result = icebreakerService.getRandom("travel");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for music mode")
    void shouldReturnForMusicMode() {
      String result = icebreakerService.getRandom("music");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for movies_series mode")
    void shouldReturnForMoviesSeriesMode() {
      String result = icebreakerService.getRandom("movies_series");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for food mode")
    void shouldReturnForFoodMode() {
      String result = icebreakerService.getRandom("food");
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return icebreaker for study mode")
    void shouldReturnForStudyMode() {
      String result = icebreakerService.getRandom("study");
      assertNotNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENGLISH_PRACTICE", "GAMING", "MUSIC"})
    @DisplayName("should return icebreaker for uppercase mode (case insensitive)")
    void shouldReturnForUppercaseMode(String mode) {
      String result = icebreakerService.getRandom(mode);
      assertNotNull(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  gaming  ", " music ", " travel "})
    @DisplayName("should return icebreaker for mode with whitespace")
    void shouldReturnForModeWithWhitespace(String mode) {
      String result = icebreakerService.getRandom(mode);
      assertNotNull(result);
    }

    @Test
    @DisplayName("should return default icebreaker for unknown mode")
    void shouldReturnDefaultForUnknownMode() {
      String result = icebreakerService.getRandom("unknown_mode_xyz");
      assertNotNull(result);
      // Should return from default list
      List<String> defaultIcebreakers =
          List.of(
              "What's something small that made you smile recently?",
              "If we could grab a coffee right now, what would you order?",
              "What song always puts you in a good mood?",
              "If you could relive one nice moment from this year, which one would you choose?");
      assertTrue(defaultIcebreakers.contains(result));
    }

    @Test
    @DisplayName("should return random icebreakers across multiple calls")
    void shouldReturnRandomIcebreakers() {
      Set<String> results = new HashSet<>();
      for (int i = 0; i < 20; i++) {
        results.add(icebreakerService.getRandom("gaming"));
      }
      // With 20 calls, we should see multiple different icebreakers
      assertTrue(results.size() > 1, "Should return different icebreakers across calls");
    }
  }
}
