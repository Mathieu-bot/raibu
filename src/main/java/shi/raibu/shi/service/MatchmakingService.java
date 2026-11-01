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

  private static final int MIN_MATCHABLE_REPUTATION = -49;
  private static final int MAX_CLAIM_ATTEMPTS = 5;

  /**
   * Finds a partner for the user and claims both of them atomically: the two rows are locked in id
   * order, their status and ban are re-checked after locking, stale ACTIVE sessions are closed and
   * both users become IN_CHAT in the same transaction. Returns empty when nobody can be claimed.
   */
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
    if (currentUser.getStatus() != User.UserStatus.SEARCHING) {
      log.info("User {} is not searching (status={}), no match", userId, currentUser.getStatus());
      return Optional.empty();
    }

    List<User> candidates =
        new ArrayList<>(
            userRepository.findByStatusAndBannedFalse(User.UserStatus.SEARCHING).stream()
                .filter(user -> !user.getId().equals(userId))
                .filter(user -> user.getReputationScore() >= MIN_MATCHABLE_REPUTATION)
                .toList());

    for (int attempt = 0; attempt < MAX_CLAIM_ATTEMPTS && !candidates.isEmpty(); attempt++) {
      User match = pickCandidate(currentUser, candidates);
      switch (claim(userId, match.getId())) {
        case CLAIMED:
          log.info("Match created between {} and {}", userId, match.getId());
          return Optional.of(match);
        case CALLER_UNAVAILABLE:
          return Optional.empty();
        case CANDIDATE_UNAVAILABLE:
          candidates.removeIf(c -> c.getId().equals(match.getId()));
          break;
      }
    }
    return Optional.empty();
  }

  private enum ClaimResult {
    CLAIMED,
    CALLER_UNAVAILABLE,
    CANDIDATE_UNAVAILABLE
  }

  private ClaimResult claim(String userId, String candidateId) {
    // Ordered pessimistic locks: concurrent claims on the same pair serialize instead of
    // deadlocking.
    userRepository.findAllByIdInOrderById(List.of(userId, candidateId));
    List<String> claimable =
        userRepository.findIdsByIdInAndStatusAndBannedFalse(
            List.of(userId, candidateId), User.UserStatus.SEARCHING);
    if (!claimable.contains(userId)) {
      return ClaimResult.CALLER_UNAVAILABLE;
    }
    if (!claimable.contains(candidateId)) {
      return ClaimResult.CANDIDATE_UNAVAILABLE;
    }

    closeActiveSessions(List.of(userId, candidateId));
    createChatSession(userId, candidateId);
    userRepository.updateStatusByIdIn(
        List.of(userId, candidateId), User.UserStatus.IN_CHAT, Instant.now());
    return ClaimResult.CLAIMED;
  }

  private void closeActiveSessions(List<String> userIds) {
    Instant now = Instant.now();
    for (ChatSession stale : chatSessionRepository.findActiveByUserIds(userIds)) {
      stale.setStatus(ChatSession.SessionStatus.ENDED);
      stale.setEndedAt(now);
      chatSessionRepository.save(stale);
      log.warn("Closed stale active session {} before creating a new match", stale.getId());
    }
  }

  private User pickCandidate(User currentUser, List<User> searchingUsers) {
    String userId = currentUser.getId();
    String countryCode = currentUser.getCountryCode();
    String currentMode = normalizeMode(currentUser.getSearchMode());
    boolean preferSameCountry =
        currentUser.getPreferSameCountry() == null || currentUser.getPreferSameCountry();
    Set<String> currentLanguages = parseCsvToSet(currentUser.getPreferredLanguages());
    Set<String> currentInterests = parseCsvToSet(currentUser.getInterests());
    String currentGender = normalizeGender(currentUser.getGender());
    Set<String> currentPreferredGenders = parseCsvToSet(currentUser.getPreferredGenders());

    int maxScore = Integer.MIN_VALUE;
    List<User> bestCandidates = new ArrayList<>();

    for (User candidate : searchingUsers) {
      int score = 0;

      String candidateMode = normalizeMode(candidate.getSearchMode());
      if (currentMode != null && currentMode.equals(candidateMode)) {
        score += 3;
      }

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

      // Reputation influence: users with higher reputationScore are preferred.
      score += candidate.getReputationScore() / 10;

      if (score > maxScore) {
        bestCandidates.clear();
        bestCandidates.add(candidate);
        maxScore = score;
      } else if (score == maxScore) {
        bestCandidates.add(candidate);
      }
    }

    if (maxScore > 0 && !bestCandidates.isEmpty()) {
      User match = bestCandidates.get(random.nextInt(bestCandidates.size()));
      log.info(
          "Matching user {} with {} using preferences (score={})", userId, match.getId(), maxScore);
      return match;
    }
    User match = searchingUsers.get(random.nextInt(searchingUsers.size()));
    log.info("Matching user {} with {} without preferences", userId, match.getId());
    return match;
  }

  @Transactional
  public void startSearching(String userId) {
    startSearching(userId, null);
  }

  @Transactional
  public void startSearching(String userId, String mode) {
    String normalizedMode = normalizeMode(mode);
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              user.setSearchMode(normalizedMode);
              user.setStatus(User.UserStatus.SEARCHING);
              user.setLastActiveAt(Instant.now());
              userRepository.save(user);
            });
    log.info("User {} started searching with mode {}", userId, normalizedMode);
  }

  @Transactional
  public void stopSearching(String userId) {
    userRepository
        .findById(userId)
        .ifPresent(
            user -> {
              user.setStatus(User.UserStatus.IDLE);
              user.setSearchMode(null);
              user.setLastActiveAt(Instant.now());
              userRepository.save(user);
            });
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

  private String normalizeMode(String mode) {
    if (mode == null) {
      return null;
    }
    String trimmed = mode.trim().toLowerCase();
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
