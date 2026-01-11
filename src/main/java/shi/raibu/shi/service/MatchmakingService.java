package shi.raibu.shi.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.repository.model.ChatSession;
import shi.raibu.shi.repository.model.User;

@Service
@AllArgsConstructor
@Slf4j
public class MatchmakingService {
  private final UserRepository userRepository;
  private final ChatSessionRepository chatSessionRepository;
  private final Random random = new Random();

  @Transactional
  public Optional<User> findRandomMatch(String userId) {
    List<User> searchingUsers =
        userRepository.findByStatusAndBannedFalse(User.UserStatus.SEARCHING).stream()
            .filter(user -> !user.getId().equals(userId))
            .toList();

    if (searchingUsers.isEmpty()) {
      return Optional.empty();
    }

    User match = searchingUsers.get(random.nextInt(searchingUsers.size()));

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
}
