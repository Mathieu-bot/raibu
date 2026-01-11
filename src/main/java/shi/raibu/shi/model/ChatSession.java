package shi.raibu.shi.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "chat_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "user1_id")
  private String user1Id;

  @Column(name = "user2_id")
  private String user2Id;

  private Instant startedAt;
  private Instant endedAt;

  @Enumerated(EnumType.STRING)
  private SessionStatus status;

  public enum SessionStatus {
    ACTIVE,
    ENDED
  }
}
