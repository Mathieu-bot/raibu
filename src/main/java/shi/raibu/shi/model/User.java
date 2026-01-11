package shi.raibu.shi.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "\"user\"")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String username;

  private String sessionId; 

  @Enumerated(EnumType.STRING)
  private UserStatus status;

  private Instant createdAt;
  private Instant lastActiveAt;

  private boolean banned;

  public enum UserStatus {
    IDLE, 
    SEARCHING, 
    IN_CHAT,
    OFFLINE
  }
}
