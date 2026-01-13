package shi.raibu.shi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "session_feedback")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionFeedback {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "from_user_id", nullable = false)
  private String fromUserId;

  @Column(name = "to_user_id", nullable = false)
  private String toUserId;

  @Column(nullable = false)
  private boolean liked;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
