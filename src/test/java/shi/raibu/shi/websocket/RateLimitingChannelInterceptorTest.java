package shi.raibu.shi.websocket;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import shi.raibu.shi.websocket.model.SignalMessage;

class RateLimitingChannelInterceptorTest {
  private final MessageChannel channel = mock(MessageChannel.class);
  private SimpMessagingTemplate template;
  private MutableClock clock;
  private RateLimitingChannelInterceptor interceptor;

  /** Clock whose time is advanced manually. */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void setUp() {
    template = mock(SimpMessagingTemplate.class);
    clock = new MutableClock();
    interceptor = new RateLimitingChannelInterceptor(template, clock);
  }

  private Message<byte[]> frame(StompCommand command, String userId) {
    StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
    if (userId != null) {
      Principal p = () -> userId;
      accessor.setUser(p);
    }
    accessor.setLeaveMutable(true);
    return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
  }

  @Test
  void framesUnderTheLimitPass() {
    for (int i = 0; i < RateLimitingChannelInterceptor.MAX_FRAMES_PER_WINDOW; i++) {
      assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "alice"), channel));
    }
    verify(template, never()).convertAndSendToUser(any(), any(), any(Object.class));
  }

  @Test
  void framesOverTheLimitAreDroppedWithRateLimitedError() {
    for (int i = 0; i < RateLimitingChannelInterceptor.MAX_FRAMES_PER_WINDOW; i++) {
      interceptor.preSend(frame(StompCommand.SEND, "alice"), channel);
    }
    assertNull(interceptor.preSend(frame(StompCommand.SEND, "alice"), channel));
    verify(template)
        .convertAndSendToUser(
            eq("alice"),
            eq("/queue/match"),
            org.mockito.ArgumentMatchers.argThat(
                (Object o) ->
                    o instanceof SignalMessage m
                        && m.getType() == SignalMessage.SignalType.ERROR
                        && "RATE_LIMITED".equals(m.getData())));
  }

  @Test
  void limitIsPerUser() {
    for (int i = 0; i < RateLimitingChannelInterceptor.MAX_FRAMES_PER_WINDOW + 5; i++) {
      interceptor.preSend(frame(StompCommand.SEND, "alice"), channel);
    }
    assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "bob"), channel));
  }

  @Test
  void windowResetsAfterItExpires() {
    for (int i = 0; i < RateLimitingChannelInterceptor.MAX_FRAMES_PER_WINDOW + 1; i++) {
      interceptor.preSend(frame(StompCommand.SEND, "alice"), channel);
    }
    clock.advance(Duration.ofMillis(RateLimitingChannelInterceptor.WINDOW_MILLIS));
    assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "alice"), channel));
  }

  @Test
  void nonSendFramesAreNotCounted() {
    for (int i = 0; i < RateLimitingChannelInterceptor.MAX_FRAMES_PER_WINDOW + 10; i++) {
      assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "alice"), channel));
    }
    assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "alice"), channel));
  }

  @Test
  void anonymousFramesPassThrough() {
    assertNotNull(interceptor.preSend(frame(StompCommand.SEND, null), channel));
  }
}
