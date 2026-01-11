package shi.raibu.shi.websocket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {
  private SignalType type;
  private String from;
  private String to;
  private Object data; // SDP offer/answer or ICE candidate

  public enum SignalType {
    OFFER,
    ANSWER,
    ICE_CANDIDATE,
    MATCH_FOUND,
    PEER_DISCONNECTED,
    ERROR
  }
}
