package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import shi.raibu.shi.model.ChatSession;
import shi.raibu.shi.model.Friendship;
import shi.raibu.shi.model.User;
import shi.raibu.shi.repository.ChatMessageRepository;
import shi.raibu.shi.repository.ChatSessionRepository;
import shi.raibu.shi.repository.FriendshipRepository;
import shi.raibu.shi.repository.UserRepository;
import shi.raibu.shi.service.DirectMessageService;
import shi.raibu.shi.service.IcebreakerService;
import shi.raibu.shi.service.MatchmakingService;
import shi.raibu.shi.websocket.model.ChatInboundMessage;
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
  @Mock FriendshipRepository friendshipRepository;
  @InjectMocks SignalingController controller;

  Principal alice = () -> "alice";

  private SignalMessage offerTo(String to) {
    return SignalMessage.builder().type(SignalMessage.SignalType.OFFER).to(to).data("sdp").build();
  }

  @Test
  void nextEndsChatThenRequeuesUserWithTheirSearchMode() {
    User user = new User();
    user.setId("alice");
    user.setSearchMode("FUN");
    when(userRepository.findById("alice")).thenReturn(Optional.of(user));
    when(matchmakingService.findRandomMatch("alice")).thenReturn(Optional.empty());

    controller.nextUser(alice);

    InOrder order = inOrder(matchmakingService);
    order.verify(matchmakingService).endChat("alice");
    order.verify(matchmakingService).startSearching("alice", "FUN");
    order.verify(matchmakingService).findRandomMatch("alice");
  }

  @Test
  void nextFromBannedUserDoesNothingButError() {
    when(matchmakingService.isUserBanned("alice")).thenReturn(true);

    controller.nextUser(alice);

    verify(matchmakingService, never()).endChat(any());
    verify(matchmakingService, never()).startSearching(any(), any());
  }

  private ChatInboundMessage chat(String content) {
    ChatInboundMessage inbound = new ChatInboundMessage();
    inbound.setTo("bob");
    inbound.setContent(content);
    return inbound;
  }

  private void activeSession() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(
            "alice", "bob", ChatSession.SessionStatus.ACTIVE))
        .thenReturn(Optional.of(new ChatSession()));
  }

  @Test
  void chatLongerThanLimitIsRejectedWithError() {
    activeSession();

    controller.sendChatMessage(alice, chat("x".repeat(SignalingController.MAX_CHAT_LENGTH + 1)));

    verify(chatMessageRepository, never()).save(any());
    ArgumentCaptor<SignalMessage> error = ArgumentCaptor.forClass(SignalMessage.class);
    verify(messagingTemplate)
        .convertAndSendToUser(eq("alice"), eq("/queue/match"), error.capture());
    assertEquals("MESSAGE_TOO_LONG", error.getValue().getData());
  }

  @Test
  void chatAtLimitAfterTrimIsAccepted() {
    activeSession();

    controller.sendChatMessage(
        alice, chat("  " + "x".repeat(SignalingController.MAX_CHAT_LENGTH) + "  "));

    verify(chatMessageRepository).save(any());
    verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/chat"), any());
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

  @Test
  void friendsCanExchangeCallSignalsWithoutActiveSession() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(friendshipRepository.findByUserId1AndUserId2("alice", "bob"))
        .thenReturn(Optional.of(new Friendship()));

    SignalMessage invite =
        SignalMessage.builder().type(SignalMessage.SignalType.CALL_INVITE).to("bob").build();
    controller.handleSignal(alice, invite);

    verify(messagingTemplate).convertAndSendToUser("bob", "/queue/signal", invite);
  }

  @Test
  void nonFriendsCannotSendCallSignalsWithoutActiveSession() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(friendshipRepository.findByUserId1AndUserId2(any(), any())).thenReturn(Optional.empty());

    controller.handleSignal(
        alice,
        SignalMessage.builder().type(SignalMessage.SignalType.CALL_INVITE).to("mallory").build());

    verify(messagingTemplate, never()).convertAndSendToUser(eq("mallory"), any(), any());
  }

  @Test
  void friendsCannotForgeSystemSignalTypes() {
    when(chatSessionRepository.findByUser1IdAndUser2IdAndStatus(any(), any(), any()))
        .thenReturn(Optional.empty());
    when(friendshipRepository.findByUserId1AndUserId2(any(), any()))
        .thenReturn(Optional.of(new Friendship()));

    controller.handleSignal(
        alice,
        SignalMessage.builder().type(SignalMessage.SignalType.MATCH_FOUND).to("bob").build());

    verify(messagingTemplate, never()).convertAndSendToUser(eq("bob"), any(), any());
  }
}
