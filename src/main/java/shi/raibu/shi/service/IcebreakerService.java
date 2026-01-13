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
          "What's something small that made you smile recently?",
          "If we could grab a coffee right now, what would you order?",
          "What song always puts you in a good mood?",
          "If you could relive one nice moment from this year, which one would you choose?");

  private static final Map<String, List<String>> MODE_ICEBREAKERS =
      Map.of(
          "english_practice",
              List.of(
                  "What's a simple way you like to practice English in your daily life?",
                  "If you could speak English perfectly tomorrow, what would you do first?",
                  "Tell me about a movie or series in English that you enjoyed."),
          "gaming",
              List.of(
                  "What game helps you relax after a long day?",
                  "If we played together, which game would you choose?",
                  "Do you remember the first game that really hooked you?"),
          "coworking",
              List.of(
                  "What are you working on today, and what would make this session feel productive?",
                  "What's one small task you want to finish during this coworking time?",
                  "What usually helps you stay focused when you work (music, silence, snacks, etc.)?"),
          "travel",
              List.of(
                  "If you could take a short weekend trip, where would you love to go?",
                  "Is there a place you've visited that you recommend to everyone?",
                  "Are you more into the beach, the city, or the mountains?"),
          "music",
              List.of(
                  "Which artist or band do you never skip when they come on?",
                  "What's the best concert you've been to, or one you'd love to see?",
                  "What kind of music do you put on when you want to focus or relax?"),
          "movies_series",
              List.of(
                  "What show are you watching or rewatching at the moment?",
                  "Is there a movie you could almost quote by heart?",
                  "Do you prefer going to the cinema or watching at home?"),
          "food",
              List.of(
                  "What's your favorite comfort food after a long day?",
                  "If we could share a meal right now, what would you pick?",
                  "Do you enjoy cooking, or do you prefer eating out or delivery?"),
          "study",
              List.of(
                  "What are you studying or learning at the moment?",
                  "Is there a subject you find surprisingly fun?",
                  "How do you usually take a break when you're studying?"));

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
