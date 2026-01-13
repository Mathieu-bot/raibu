package shi.raibu.shi.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;

@Service
@AllArgsConstructor
@Slf4j
public class MatchmakingService {
  private final UserRepository userRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final Random random = new Random();

  @Transactional
  public Optional<User> findRandomMatch(String userId) {
    Optional<User> currentUserOpt = userRepository.findById(userId);
    if (currentUserOpt.isEmpty()) {
      log.warn("Cannot find match for non-existing user {}", userId);
      return Optional.empty();
    }

    User currentUser = currentUserOpt.get();
    if (currentUser.isBanned()) {
      log.info("User {} is banned and cannot be matched", userId);
      return Optional.empty();
    }
    String countryCode = currentUser.getCountryCode();

    List<User> searchingUsers =
        userRepository.findByStatusAndBannedFalse(User.UserStatus.SEARCHING).stream()
            .filter(user -> !user.getId().equals(userId))
            .toList();

    if (searchingUsers.isEmpty()) {
      return Optional.empty();
    }

    boolean preferSameCountry =
        currentUser.getPreferSameCountry() == null || currentUser.getPreferSameCountry();
    Set<String> currentLanguages = parseCsvToSet(currentUser.getPreferredLanguages());
    Set<String> currentInterests = parseCsvToSet(currentUser.getInterests());
    String currentGender = normalizeGender(currentUser.getGender());
    Set<String> currentPreferredGenders = parseCsvToSet(currentUser.getPreferredGenders());

    int maxScore = Integer.MIN_VALUE;
    List<User> bestCandidates = new ArrayList<>();

    for (User candidate : searchingUsers) {
      // Exclude candidates with very low reputation
      if (candidate.getReputationScore() <= -50) {
        continue;
      }

      int score = 0;

      if (preferSameCountry
          && countryCode != null
          && candidate.getCountryCode() != null
          && candidate.getCountryCode().equalsIgnoreCase(countryCode)) {
        score += 2;
      }

      Set<String> candidateLanguages = parseCsvToSet(candidate.getPreferredLanguages());
      if (!Collections.disjoint(currentLanguages, candidateLanguages)) {
        score += 1;
      }

      Set<String> candidateInterests = parseCsvToSet(candidate.getInterests());
      if (!Collections.disjoint(currentInterests, candidateInterests)) {
        score += 2;
      }

      String candidateGender = normalizeGender(candidate.getGender());
      Set<String> candidatePreferredGenders = parseCsvToSet(candidate.getPreferredGenders());

      if (!currentPreferredGenders.isEmpty()
          && candidateGender != null
          && currentPreferredGenders.contains(candidateGender)) {
        score += 2;
      }

      if (!candidatePreferredGenders.isEmpty()
          && currentGender != null
          && candidatePreferredGenders.contains(currentGender)) {
        score += 2;
      }

      // Reputation influence: users with higher reputationScore are preferred,
      // while users with strongly negative reputation are penalized.
      score += candidate.getReputationScore() / 10;

      if (score > maxScore) {
        bestCandidates.clear();
        bestCandidates.add(candidate);
        maxScore = score;
      } else if (score == maxScore) {
        bestCandidates.add(candidate);
      }
    }

    User match;
    if (maxScore > 0 && !bestCandidates.isEmpty()) {
      match = bestCandidates.get(random.nextInt(bestCandidates.size()));
      log.info(
          "Matching user {} with {} using preferences (score={})", userId, match.getId(), maxScore);
    } else {
      match = searchingUsers.get(random.nextInt(searchingUsers.size()));
      log.info("Matching user {} with {} without preferences", userId, match.getId());
    }

    createChatSession(userId, match.getId());

    updateUserStatus(userId, User.UserStatus.IN_CHAT);
    updateUserStatus(match.getId(), User.UserStatus.IN_CHAT);

    log.info("Match created between {} and {}", userId, match.getId());
    return Optional.of(match);
  }

  @Transactional
  public void startSearching(String userId) {
    updateUserStatus(userId, User.UserStatus.SEARCHING);
    log.info("User {} started searching", userId);
  }

  @Transactional
  public void stopSearching(String userId) {
    updateUserStatus(userId, User.UserStatus.IDLE);
    log.info("User {} stopped searching", userId);
  }

  @Transactional
  public void endChat(String userId) {

    List<ChatSession> sessions = chatSessionRepository.findByUser1IdOrUser2Id(userId, userId);

    sessions.stream()
        .filter(s -> s.getStatus() == ChatSession.SessionStatus.ACTIVE)
        .forEach(
            s -> {
              s.setStatus(ChatSession.SessionStatus.ENDED);
              s.setEndedAt(Instant.now());
              chatSessionRepository.save(s);
            });

    updateUserStatus(userId, User.UserStatus.IDLE);
    log.info("User {} ended chat", userId);
  }

  private void createChatSession(String user1Id, String user2Id) {
    ChatSession session =
        ChatSession.builder()
            .user1Id(user1Id)
            .user2Id(user2Id)
            .startedAt(Instant.now())
            .status(ChatSession.SessionStatus.ACTIVE)
            .build();
    chatSessionRepository.save(session);
  }

  private Set<String> parseCsvToSet(String value) {
    if (value == null || value.isBlank()) {
      return Collections.emptySet();
    }
    String[] parts = value.split(",");
    Set<String> result = new HashSet<>();
    for (String part : parts) {
      String trimmed = part.trim().toLowerCase();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  private String normalizeGender(String gender) {
    if (gender == null) {
      return null;
    }
    String trimmed = gender.trim().toLowerCase();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void updateUserStatus(String userId, User.UserStatus status) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              user.setStatus(status);
              user.setLastActiveAt(Instant.now());
              userRepository.save(user);
            });
  }

  public boolean isUserBanned(String userId) {
    return userRepository.findById(userId).map(User::isBanned).orElse(false);
  }
}
