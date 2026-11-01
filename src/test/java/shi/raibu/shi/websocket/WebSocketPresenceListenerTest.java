package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import shi.raibu.shi.service.MatchmakingService;

class WebSocketPresenceListenerTest {
  private MatchmakingService matchmakingService;
  private FakeScheduler scheduler;
  private WebSocketPresenceListener listener;

  /** Records scheduled tasks instead of running them, so tests control time. */
  private static final class FakeScheduler {
    final TaskScheduler scheduler = mock(TaskScheduler.class);
    final List<Runnable> tasks = new ArrayList<>();
    final List<ScheduledFuture<?>> futures = new ArrayList<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    FakeScheduler() {
      when(scheduler.schedule(any(Runnable.class), any(Instant.class)))
          .thenAnswer(
              inv -> {
                tasks.add(inv.getArgument(0));
                ScheduledFuture future = mock(ScheduledFuture.class);
                futures.add(future);
                return future;
              });
    }
  }

  @BeforeEach
  void setUp() {
    matchmakingService = mock(MatchmakingService.class);
    scheduler = new FakeScheduler();
    listener = new WebSocketPresenceListener(matchmakingService, scheduler.scheduler);
  }

  private static Message<byte[]> message(String sessionId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
    accessor.setSessionId(sessionId);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  private void connect(String userId, String sessionId) {
    Principal p = () -> userId;
    listener.onConnected(new SessionConnectedEvent(this, message(sessionId), p));
  }

  private void disconnect(String userId, String sessionId) {
    Principal p = () -> userId;
    listener.onDisconnect(
        new SessionDisconnectEvent(this, message(sessionId), sessionId, CloseStatus.NORMAL, p));
  }

  @Test
  void lastSessionClosingEndsTheChatAfterGracePeriod() {
    connect("alice", "s1");
    disconnect("alice", "s1");

    verify(matchmakingService, never()).endChat(any());
    assertEquals(1, scheduler.tasks.size());

    scheduler.tasks.get(0).run();

    verify(matchmakingService).endChat("alice");
  }

  @Test
  void userWithAnotherOpenSessionIsNotReleased() {
    connect("alice", "s1");
    connect("alice", "s2");
    disconnect("alice", "s1");

    assertTrue(scheduler.tasks.isEmpty());
    assertEquals(1, listener.openSessions("alice"));
    verify(matchmakingService, never()).endChat(any());
  }

  @Test
  void reconnectingWithinGracePeriodCancelsTheRelease() {
    connect("alice", "s1");
    disconnect("alice", "s1");
    connect("alice", "s2");

    verify(scheduler.futures.get(0)).cancel(false);

    // Even if the cancelled task still fires, the user has a session and is not released.
    scheduler.tasks.get(0).run();
    verify(matchmakingService, never()).endChat(any());
  }

  @Test
  void duplicateDisconnectEventsScheduleOnlyOnce() {
    connect("alice", "s1");
    disconnect("alice", "s1");
    disconnect("alice", "s1");

    assertEquals(1, scheduler.tasks.size());
  }

  @Test
  void eachUserIsReleasedIndependently() {
    connect("alice", "s1");
    connect("bob", "s2");
    disconnect("alice", "s1");

    scheduler.tasks.get(0).run();

    verify(matchmakingService, times(1)).endChat("alice");
    verify(matchmakingService, never()).endChat("bob");
    assertEquals(1, listener.openSessions("bob"));
  }
}
