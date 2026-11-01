package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.repository.ChatMessageRepository;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.DirectMessageService;
import shi.raibu.shi.service.IcebreakerService;
import shi.raibu.shi.service.MatchmakingService;
import shi.raibu.shi.websocket.model.SignalMessage;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignalingControllerTest {
  @Mock SimpMessagingTemplate messagingTemplate;
  @Mock MatchmakingService matchmakingService;
  @Mock ChatSessionRepository chatSessionRepository;
  @Mock ChatMessageRepository chatMessageRepository;
  @Mock UserRepository userRepository;
  @Mock IcebreakerService icebreakerService;
  @Mock DirectMessageService directMessageService;
  @InjectMocks SignalingController controller;

  Principal alice = () -> "alice";

  private SignalMessage offerTo(String to) {
    return SignalMessage.builder().type(SignalMessage.SignalType.OFFER).to(to).data("sdp").build();
  }

  @Test
  void signalIsRelayedWhenSessionIsActive() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(
            "alice", "bob", ChatSession.SessionStatus.ACTIVE))
        .thenReturn(Optional.of(new ChatSession()));

    SignalMessage msg = offerTo("bob");
    controller.handleSignal(alice, msg);

    verify(messagingTemplate).convertAndSendToUser("bob", "/queue/signal", msg);
  }

  @Test
  void signalIsRelayedWhenSessionStoredInReverseOrder() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(
            "alice", "bob", ChatSession.SessionStatus.ACTIVE))
        .thenReturn(Optional.empty());
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(
            "bob", "alice", ChatSession.SessionStatus.ACTIVE))
        .thenReturn(Optional.of(new ChatSession()));

    controller.handleSignal(alice, offerTo("bob"));

    verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/signal"), any());
  }

  @Test
  void signalWithoutActiveSessionIsDroppedWithError() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());

    controller.handleSignal(alice, offerTo("mallory"));

    verify(messagingTemplate, never()).convertAndSendToUser(eq("mallory"), any(), any());
    ArgumentCaptor<SignalMessage> error = ArgumentCaptor.forClass(SignalMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("alice"), eq("/queue/match"), error.capture());
    assertEquals(SignalMessage.SignalType.ERROR, error.getValue().getType());
    assertEquals("NO_ACTIVE_SESSION", error.getValue().getData());
  }
}
