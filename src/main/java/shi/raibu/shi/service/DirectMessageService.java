package shi.raibu.shi.service;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.DirectMessage;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.repository.DirectMessageRepository;
import shi.raibu.shi.repository.FriendshipRepository;

@Service
@AllArgsConstructor
public class DirectMessageService {

  private final DirectMessageRepository directMessageRepository;
  private final FriendshipRepository friendshipRepository;
  private final NotificationService notificationService;

  public List<DirectMessage> getConversation(String currentUserId, String friendId) {
    ensureFriendship(currentUserId, friendId);
    return directMessageRepository
        .findBySenderIdAndRecipientIdOrSenderIdAndRecipientIdOrderBySentAtAsc(
            currentUserId, friendId, friendId, currentUserId);
  }

  public DirectMessage sendMessage(String senderId, String recipientId, String content) {
    ensureFriendship(senderId, recipientId);

    if (content == null) {
      throw new IllegalArgumentException("content cannot be null");
    }
    String trimmed = content.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("content cannot be blank");
    }

    DirectMessage message =
        DirectMessage.builder()
            .senderId(senderId)
            .recipientId(recipientId)
            .content(trimmed)
            .sentAt(Instant.now())
            .build();

    DirectMessage saved = directMessageRepository.save(message);

    String dataJson =
        String.format("{\"senderId\":\"%s\",\"directMessageId\":\"%s\"}", senderId, saved.getId());

    notificationService.notifyUser(
        recipientId, NotificationType.NEW_DM, "You have a new message.", dataJson);

    return saved;
  }

  private void ensureFriendship(String userIdA, String userIdB) {
    if (userIdA.equals(userIdB)) {
      throw new IllegalArgumentException("Cannot DM yourself");
    }
    String user1 = userIdA.compareTo(userIdB) < 0 ? userIdA : userIdB;
    String user2 = userIdA.compareTo(userIdB) < 0 ? userIdB : userIdA;

    friendshipRepository
        .findByUserId1AndUserId2(user1, user2)
        .orElseThrow(() -> new IllegalStateException("Users are not friends"));
  }
}
