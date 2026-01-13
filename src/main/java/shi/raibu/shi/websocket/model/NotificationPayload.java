package shi.raibu.shi.websocket.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import shi.raibu.shi.model.Notification.NotificationType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
  private String id;
  private NotificationType type;
  private String message;
  private String data;
  private Instant createdAt;
  private boolean read;
}
