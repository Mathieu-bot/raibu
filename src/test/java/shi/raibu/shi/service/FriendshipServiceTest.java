package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.repository.FriendshipRepository;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

  @Mock private FriendshipRepository friendshipRepository;
  @Mock private NotificationService notificationService;
  @Mock private CachedFriendshipService cachedFriendshipService;

  private FriendshipService friendshipService;

  @BeforeEach
  void setUp() {
    friendshipService =
        new FriendshipService(friendshipRepository, notificationService, cachedFriendshipService);
  }

  @Nested
  @DisplayName("createFriendshipIfAbsent()")
  class CreateFriendshipIfAbsentTests {

    @Test
    @DisplayName("should throw exception when user tries to friend themselves")
    void shouldThrowExceptionWhenSelfFriendship() {
      assertThrows(
          IllegalArgumentException.class,
          () -> friendshipService.createFriendshipIfAbsent("user-1", "user-1"));

      verifyNoInteractions(friendshipRepository, notificationService);
    }

    @Test
    @DisplayName("should return existing friendship if already exists")
    void shouldReturnExistingFriendship() {
      Friendship existingFriendship =
          Friendship.builder().userId1("user-1").userId2("user-2").createdAt(Instant.now()).build();

      when(friendshipRepository.findByUserId1AndUserId2("user-1", "user-2"))
          .thenReturn(Optional.of(existingFriendship));

      Friendship result = friendshipService.createFriendshipIfAbsent("user-1", "user-2");

      assertEquals(existingFriendship, result);
      verify(friendshipRepository).findByUserId1AndUserId2("user-1", "user-2");
      verify(friendshipRepository, never()).save(any(Friendship.class));
      verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("should create new friendship with correct user order")
    void shouldCreateNewFriendshipWithCorrectOrder() {
      Friendship newFriendship =
          Friendship.builder().userId1("user-1").userId2("user-2").createdAt(Instant.now()).build();

      when(friendshipRepository.findByUserId1AndUserId2("user-1", "user-2"))
          .thenReturn(Optional.empty());
      when(friendshipRepository.save(any(Friendship.class))).thenReturn(newFriendship);

      Friendship result = friendshipService.createFriendshipIfAbsent("user-2", "user-1");

      assertNotNull(result);
      verify(friendshipRepository).findByUserId1AndUserId2("user-1", "user-2");
      verify(friendshipRepository)
          .save(argThat(f -> "user-1".equals(f.getUserId1()) && "user-2".equals(f.getUserId2())));
      verify(notificationService, times(2))
          .notifyUser(
              anyString(),
              any(shi.raibu.shi.model.Notification.NotificationType.class),
              anyString(),
              anyString());
    }

    @Test
    @DisplayName("should notify both users when friendship is created")
    void shouldNotifyBothUsersWhenFriendshipCreated() {
      Friendship newFriendship =
          Friendship.builder().userId1("user-1").userId2("user-2").createdAt(Instant.now()).build();

      when(friendshipRepository.findByUserId1AndUserId2("user-1", "user-2"))
          .thenReturn(Optional.empty());
      when(friendshipRepository.save(any(Friendship.class))).thenReturn(newFriendship);

      friendshipService.createFriendshipIfAbsent("user-1", "user-2");

      verify(notificationService)
          .notifyUser(
              eq("user-1"),
              eq(shi.raibu.shi.model.Notification.NotificationType.MATCH_CONFIRMED),
              eq("You have a new mutual match."),
              contains("user-2"));

      verify(notificationService)
          .notifyUser(
              eq("user-2"),
              eq(shi.raibu.shi.model.Notification.NotificationType.MATCH_CONFIRMED),
              eq("You have a new mutual match."),
              contains("user-1"));
    }
  }
}
