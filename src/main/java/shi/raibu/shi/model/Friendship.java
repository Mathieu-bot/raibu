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
@Table(name = "friendship")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private String id;

  @Column(name = "user_id_1", nullable = false)
  private String userId1;

  @Column(name = "user_id_2", nullable = false)
  private String userId2;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
