package shi.raibu.shi.websocket.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectMessagePayload {

  private String id;
  private String senderId;
  private String recipientId;
  private String content;
  private Instant sentAt;
}
