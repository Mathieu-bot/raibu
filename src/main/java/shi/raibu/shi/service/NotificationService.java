package shi.raibu.shi.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import shi.raibu.shi.model.Notification;
import shi.raibu.shi.model.Notification.NotificationType;
import shi.raibu.shi.repository.NotificationRepository;
import shi.raibu.shi.websocket.model.NotificationPayload;

@Service
@AllArgsConstructor
@Slf4j
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final SimpMessagingTemplate messagingTemplate;

  public Notification notifyUser(
      String userId, NotificationType type, String message, String data) {
    Instant now = Instant.now();
    Notification notification =
        Notification.builder()
            .userId(userId)
            .type(type)
            .message(message)
            .data(data)
            .createdAt(now)
            .read(false)
            .build();

    Notification saved = notificationRepository.save(notification);

    NotificationPayload payload = toPayload(saved);

    try {
      messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", payload);
    } catch (Exception e) {
      log.warn("Failed to send notification over WebSocket to user {}", userId, e);
    }

    return saved;
  }

  public List<NotificationPayload> getNotifications(String userId, boolean onlyUnread, int limit) {
    int pageSize = Math.max(1, Math.min(limit, 100));
    PageRequest pageRequest = PageRequest.of(0, pageSize);

    Page<Notification> page;
    if (onlyUnread) {
      page =
          notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageRequest);
    } else {
      page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }

    return page.getContent().stream().map(this::toPayload).collect(Collectors.toList());
  }

  public boolean markAsRead(String userId, String notificationId) {
    Optional<Notification> notificationOpt = notificationRepository.findById(notificationId);
    if (notificationOpt.isEmpty()) {
      return false;
    }

    Notification notification = notificationOpt.get();
    if (!notification.getUserId().equals(userId)) {
      return false;
    }

    if (!notification.isRead()) {
      notification.setRead(true);
      notification.setReadAt(Instant.now());
      notificationRepository.save(notification);
    }

    return true;
  }

  private NotificationPayload toPayload(Notification notification) {
    return NotificationPayload.builder()
        .id(notification.getId())
        .type(notification.getType())
        .message(notification.getMessage())
        .data(notification.getData())
        .createdAt(notification.getCreatedAt())
        .read(notification.isRead())
        .build();
  }
}
