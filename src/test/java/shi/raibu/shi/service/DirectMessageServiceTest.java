package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import shi.raibu.shi.model.DirectMessage;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.repository.DirectMessageRepository;
import shi.raibu.shi.repository.FriendshipRepository;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

  @Mock private DirectMessageRepository directMessageRepository;
  @Mock private FriendshipRepository friendshipRepository;
  @Mock private NotificationService notificationService;

  private DirectMessageService service;

  @BeforeEach
  void setUp() {
    service =
        new DirectMessageService(
            directMessageRepository, friendshipRepository, notificationService);
    when(friendshipRepository.findByUserId1AndUserId2("a", "b"))
        .thenReturn(Optional.of(new Friendship()));
  }

  @Test
  void rejectsContentOverTwoThousandCharacters() {
    assertThrows(
        IllegalArgumentException.class, () -> service.sendMessage("a", "b", "x".repeat(2001)));
    verify(directMessageRepository, never()).save(any());
  }

  @Test
  void acceptsExactlyTwoThousandCharacters() {
    when(directMessageRepository.save(any(DirectMessage.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    assertDoesNotThrow(() -> service.sendMessage("a", "b", "x".repeat(2000)));
  }
}
