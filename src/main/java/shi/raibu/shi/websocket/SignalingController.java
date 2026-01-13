package shi.raibu.shi.websocket;

import java.security.Principal;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import shi.raibu.shi.model.User;
import shi.raibu.shi.service.MatchmakingService;
import shi.raibu.shi.websocket.model.SignalMessage;

@Controller
@AllArgsConstructor
@Slf4j
public class SignalingController {
  private final SimpMessagingTemplate messagingTemplate;
  private final MatchmakingService matchmakingService;

  private void sendErrorToUser(String userId, String code) {
    SignalMessage error =
        SignalMessage.builder()
            .type(SignalMessage.SignalType.ERROR)
            .from("system")
            .to(userId)
            .data(code)
            .build();
    messagingTemplate.convertAndSendToUser(userId, "/queue/match", error);
  }

  /** Search for a random match */
  @MessageMapping("/search")
  public void searchForMatch(Principal principal, SimpMessageHeaderAccessor headerAccessor) {
    String userId = principal.getName();
    String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
    log.info("User {} searching for match (session: {})", userId, sessionId);

    if (matchmakingService.isUserBanned(userId)) {
      log.info("Blocked banned user {} from searching for match", userId);
      sendErrorToUser(userId, "USER_BANNED");
      return;
    }

    matchmakingService.startSearching(userId);

    handleMatchForUser(userId);
  }

  private void handleMatchForUser(String userId) {
    Optional<User> match = matchmakingService.findRandomMatch(userId);

    if (match.isPresent()) {
      User matchedUser = match.get();

      SignalMessage matchMessage =
          SignalMessage.builder()
              .type(SignalMessage.SignalType.MATCH_FOUND)
              .from("system")
              .to(userId)
              .data(matchedUser.getId())
              .build();

      messagingTemplate.convertAndSendToUser(userId, "/queue/match", matchMessage);

      SignalMessage matchMessageForPeer =
          SignalMessage.builder()
              .type(SignalMessage.SignalType.MATCH_FOUND)
              .from("system")
              .to(matchedUser.getId())
              .data(userId)
              .build();

      messagingTemplate.convertAndSendToUser(
          matchedUser.getId(), "/queue/match", matchMessageForPeer);

      log.info("Match established between {} and {}", userId, matchedUser.getId());
    }
  }

  /** Send WebRTC signal (offer, answer, ICE candidate) */
  @MessageMapping("/signal")
  public void handleSignal(@Payload SignalMessage message) {
    log.info("Signal {} from {} to {}", message.getType(), message.getFrom(), message.getTo());

    messagingTemplate.convertAndSendToUser(message.getTo(), "/queue/signal", message);
  }

  /** Skip to the next user ("Next" button) */
  @MessageMapping("/next")
  public void nextUser(Principal principal) {
    String userId = principal.getName();
    log.info("User {} wants to skip to next", userId);

    if (matchmakingService.isUserBanned(userId)) {
      log.info("Blocked banned user {} from requesting next", userId);
      sendErrorToUser(userId, "USER_BANNED");
      return;
    }

    matchmakingService.endChat(userId);

    handleMatchForUser(userId);
  }

  /** Stop searching */
  @MessageMapping("/stop")
  public void stopSearching(Principal principal) {
    String userId = principal.getName();
    log.info("User {} stopped searching", userId);
    matchmakingService.stopSearching(userId);
  }
}
