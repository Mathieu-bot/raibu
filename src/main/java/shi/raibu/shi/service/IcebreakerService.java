package shi.raibu.shi.service;

import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class IcebreakerService {

  private static final List<String> DEFAULT_ICEBREAKERS =
      List.of(
          "If you could travel tomorrow, where would you go?",
          "What's your favorite way to relax after a long day?",
          "What music are you listening to a lot lately?",
          "What's something you enjoy that most people don't know about?");

  private static final Map<String, List<String>> MODE_ICEBREAKERS =
      Map.of(
          "english_practice",
              List.of(
                  "What's your favorite English word and why?",
                  "How did you start learning English?"),
          "gaming",
              List.of(
                  "What game are you playing the most right now?",
                  "PC, console or mobile – what's your favorite way to play?"));

  private final Random random = new Random();

  public String getRandom(String mode) {
    String key = normalizeMode(mode);
    List<String> list = MODE_ICEBREAKERS.getOrDefault(key, DEFAULT_ICEBREAKERS);
    if (list == null || list.isEmpty()) {
      return null;
    }
    return list.get(random.nextInt(list.size()));
  }

  private String normalizeMode(String mode) {
    if (mode == null) {
      return null;
    }
    String trimmed = mode.trim().toLowerCase();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
