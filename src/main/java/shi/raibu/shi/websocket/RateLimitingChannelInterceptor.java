package shi.raibu.shi.websocket;

import java.security.Principal;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import shi.raibu.shi.websocket.model.SignalMessage;

/**
 * Limits how many STOMP SEND frames a user may emit in a fixed window. Frames over the limit are
 * dropped and the user receives a {@code RATE_LIMITED} error on {@code /user/queue/match}. The
 * counters live in memory, so the limit is per instance.
 */
@Component
@Slf4j
public class RateLimitingChannelInterceptor implements ChannelInterceptor {
  static final int MAX_FRAMES_PER_WINDOW = 100;
  static final long WINDOW_MILLIS = 10_000;
  private static final int CLEANUP_THRESHOLD = 10_000;

  private final SimpMessagingTemplate messagingTemplate;
  private final Clock clock;
  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  private static final class Window {
    long startMillis;
    int count;
  }

  @Autowired
  public RateLimitingChannelInterceptor(@Lazy SimpMessagingTemplate messagingTemplate) {
    this(messagingTemplate, Clock.systemUTC());
  }

  RateLimitingChannelInterceptor(SimpMessagingTemplate messagingTemplate, Clock clock) {
    this.messagingTemplate = messagingTemplate;
    this.clock = clock;
  }

  @Override
  public Message<?> preSend(Message<?> message, MessageChannel channel) {
    StompHeaderAccessor accessor =
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    if (accessor == null || accessor.getCommand() != StompCommand.SEND) {
      return message;
    }
    Principal user = accessor.getUser();
    if (user == null) {
      return message;
    }
    String userId = user.getName();
    if (!tryAcquire(userId)) {
      log.warn("Rate limit exceeded for user {}, dropping frame", userId);
      SignalMessage error =
          SignalMessage.builder()
              .type(SignalMessage.SignalType.ERROR)
              .from("system")
              .to(userId)
              .data("RATE_LIMITED")
              .build();
      messagingTemplate.convertAndSendToUser(userId, "/queue/match", error);
      return null;
    }
    return message;
  }

  private boolean tryAcquire(String userId) {
    long now = clock.millis();
    if (windows.size() > CLEANUP_THRESHOLD) {
      windows.entrySet().removeIf(e -> now - e.getValue().startMillis >= WINDOW_MILLIS);
    }
    Window window = windows.computeIfAbsent(userId, k -> new Window());
    synchronized (window) {
      if (now - window.startMillis >= WINDOW_MILLIS) {
        window.startMillis = now;
        window.count = 0;
      }
      window.count++;
      return window.count <= MAX_FRAMES_PER_WINDOW;
    }
  }
}
