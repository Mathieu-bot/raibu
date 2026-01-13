package shi.raibu.shi.websocket;

import java.security.Principal;
import java.time.Instant;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import shi.raibu.shi.model.ChatMessage;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.ChatMessageRepository;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.IcebreakerService;
import shi.raibu.shi.service.MatchmakingService;
import shi.raibu.shi.websocket.model.ChatInboundMessage;
import shi.raibu.shi.websocket.model.ChatMessagePayload;
import shi.raibu.shi.websocket.model.SearchRequest;
import shi.raibu.shi.websocket.model.SignalMessage;

@Controller
@AllArgsConstructor
@Slf4j
public class SignalingController {
  private final SimpMessagingTemplate messagingTemplate;
  private final MatchmakingService matchmakingService;
  private final ChatSessionRepository chatSessionRepository;
  private final ChatMessageRepository chatMessageRepository;
  private final UserRepository userRepository;
  private final IcebreakerService icebreakerService;

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
  public void searchForMatch(
      Principal principal,
      @Payload(required = false) SearchRequest request,
      SimpMessageHeaderAccessor headerAccessor) {
    String userId = principal.getName();
    String sessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
    String mode = request != null ? request.getMode() : null;
    log.info("User {} searching for match (session: {}, mode: {})", userId, sessionId, mode);

    if (matchmakingService.isUserBanned(userId)) {
      log.info("Blocked banned user {} from searching for match", userId);
      sendErrorToUser(userId, "USER_BANNED");
      return;
    }

    matchmakingService.startSearching(userId, mode);

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

      // Send an icebreaker prompt to both users, based on the current user's search mode.
      userRepository
          .findById(userId)
          .map(User::getSearchMode)
          .ifPresent(
              mode -> {
                String question = icebreakerService.getRandom(mode);
                if (question != null && !question.isBlank()) {
                  SignalMessage icebreakerForUser =
                      SignalMessage.builder()
                          .type(SignalMessage.SignalType.ICEBREAKER)
                          .from("system")
                          .to(userId)
                          .data(question)
                          .build();

                  SignalMessage icebreakerForPeer =
                      SignalMessage.builder()
                          .type(SignalMessage.SignalType.ICEBREAKER)
                          .from("system")
                          .to(matchedUser.getId())
                          .data(question)
                          .build();

                  messagingTemplate.convertAndSendToUser(userId, "/queue/match", icebreakerForUser);
                  messagingTemplate.convertAndSendToUser(
                      matchedUser.getId(), "/queue/match", icebreakerForPeer);
                }
              });
    }
  }

  /** Send WebRTC signal (offer, answer, ICE candidate) */
  @MessageMapping("/signal")
  public void handleSignal(@Payload SignalMessage message) {
    log.info("Signal {} from {} to {}", message.getType(), message.getFrom(), message.getTo());

    messagingTemplate.convertAndSendToUser(message.getTo(), "/queue/signal", message);
  }

  /** Send text chat message during an active session */
  @MessageMapping("/chat")
  public void sendChatMessage(Principal principal, @Payload ChatInboundMessage inbound) {
    String senderId = principal.getName();
    String recipientId = inbound.getTo();
    log.info("Chat message from {} to {}", senderId, recipientId);

    if (recipientId == null || recipientId.isBlank()) {
      log.warn("Ignoring chat message from {} with no recipient", senderId);
      return;
    }

    if (matchmakingService.isUserBanned(senderId)) {
      log.info("Blocked banned user {} from sending chat message", senderId);
      sendErrorToUser(senderId, "USER_BANNED");
      return;
    }

    Optional<ChatSession> sessionOpt =
        chatSessionRepository
            .findByUser1IdAndUser2IdAndStatus(
                senderId, recipientId, ChatSession.SessionStatus.ACTIVE)
            .or(
                () ->
                    chatSessionRepository.findByUser1IdAndUser2IdAndStatus(
                        recipientId, senderId, ChatSession.SessionStatus.ACTIVE));

    if (sessionOpt.isEmpty()) {
      log.info("No active session between {} and {} for chat", senderId, recipientId);
      sendErrorToUser(senderId, "NO_ACTIVE_SESSION");
      return;
    }

    ChatSession session = sessionOpt.get();

    String content = inbound.getContent();
    if (content == null) {
      log.warn("Ignoring empty chat message from {}", senderId);
      return;
    }
    content = content.trim();
    if (content.isEmpty()) {
      log.warn("Ignoring blank chat message from {}", senderId);
      return;
    }

    Instant now = Instant.now();
    ChatMessage chatMessage =
        ChatMessage.builder()
            .sessionId(session.getId())
            .senderId(senderId)
            .content(content)
            .sentAt(now)
            .build();
    chatMessageRepository.save(chatMessage);

    ChatMessagePayload payload =
        ChatMessagePayload.builder()
            .sessionId(session.getId())
            .senderId(senderId)
            .content(content)
            .sentAt(now)
            .build();

    SignalMessage outgoing =
        SignalMessage.builder()
            .type(SignalMessage.SignalType.CHAT_TEXT)
            .from(senderId)
            .to(recipientId)
            .data(payload)
            .build();

    messagingTemplate.convertAndSendToUser(recipientId, "/queue/chat", outgoing);
    messagingTemplate.convertAndSendToUser(senderId, "/queue/chat", outgoing);
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
