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
@Table(name = "direct_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessage {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "sender_id", nullable = false)
  private String senderId;

  @Column(name = "recipient_id", nullable = false)
  private String recipientId;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "sent_at", nullable = false)
  private Instant sentAt;
}
