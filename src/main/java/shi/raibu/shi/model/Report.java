package shi.raibu.shi.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  private String reporterId; 
  private String reportedUserId; 
  private String sessionId; 

  @Enumerated(EnumType.STRING)
  private ReportReason reason;

  private String description;
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  private ReportStatus status;

  public enum ReportReason {
    INAPPROPRIATE_BEHAVIOR,
    NUDITY,
    HARASSMENT,
    SPAM,
    OTHER
  }

  public enum ReportStatus {
    PENDING,
    REVIEWED,
    ACTIONED
  }
}
