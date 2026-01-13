package shi.raibu.shi.service;

import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.repository.FriendshipRepository;

@Service
@AllArgsConstructor
@Slf4j
public class FriendshipService {

  private final FriendshipRepository friendshipRepository;
  private final NotificationService notificationService;

  public Friendship createFriendshipIfAbsent(String userIdA, String userIdB) {
    if (userIdA.equals(userIdB)) {
      throw new IllegalArgumentException("Cannot create friendship with self");
    }

    String user1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
    String user2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;

    Optional<Friendship> existing = friendshipRepository.findByUserId1AndUserId2(user1, user2);
    if (existing.isPresent()) {
      return existing.get();
    }

    Friendship friendship =
        Friendship.builder().userId1(user1).userId2(user2).createdAt(Instant.now()).build();

    try {
      Friendship saved = friendshipRepository.save(friendship);

      // Notify both users that they now have a mutual match / friend.
      String dataJsonForA = String.format("{\"friendId\":\"%s\"}", userIdB);
      String dataJsonForB = String.format("{\"friendId\":\"%s\"}", userIdA);

      notificationService.notifyUser(
          userIdA, NotificationType.MATCH_CONFIRMED, "You have a new mutual match.", dataJsonForA);
      notificationService.notifyUser(
          userIdB, NotificationType.MATCH_CONFIRMED, "You have a new mutual match.", dataJsonForB);

      return saved;
    } catch (DataIntegrityViolationException e) {
      log.warn("Friendship already exists between {} and {}", user1, user2, e);
      // In case of race condition, load the existing one.
      return friendshipRepository.findByUserId1AndUserId2(user1, user2).orElseThrow(() -> e);
    }
  }
}
