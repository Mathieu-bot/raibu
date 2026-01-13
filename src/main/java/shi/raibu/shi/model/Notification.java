package shi.raibu.shi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notification")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "user_id", nullable = false)
  private String userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private NotificationType type;

  @Column(nullable = false)
  private String message;

  @Column(columnDefinition = "text")
  private String data;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "read", nullable = false)
  private boolean read;

  @Column(name = "read_at")
  private Instant readAt;

  public enum NotificationType {
    USER_BANNED,
    USER_UNBANNED,
    REPORT_ACTIONED
  }
}
