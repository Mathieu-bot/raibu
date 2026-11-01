package shi.raibu.shi.websocket;

import jakarta.annotation.PreDestroy;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import shi.raibu.shi.service.MatchmakingService;

/**
 * Tracks the open WebSocket sessions of each user. When the last session of a user closes, the
 * user's chat is ended after a grace period, unless they reconnect first (page reload, network
 * blip, another tab).
 */
@Component
@Slf4j
public class WebSocketPresenceListener {
  static final Duration GRACE_PERIOD = Duration.ofSeconds(10);

  private final MatchmakingService matchmakingService;
  private final TaskScheduler scheduler;
  private final ThreadPoolTaskScheduler ownedScheduler;

  private final Map<String, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
  private final Map<String, ScheduledFuture<?>> pendingReleases = new ConcurrentHashMap<>();

  /**
   * Uses a private scheduler so it never competes with other {@code TaskScheduler} beans of the
   * application.
   */
  @Autowired
  public WebSocketPresenceListener(MatchmakingService matchmakingService) {
    this.matchmakingService = matchmakingService;
    this.ownedScheduler = new ThreadPoolTaskScheduler();
    this.ownedScheduler.setPoolSize(1);
    this.ownedScheduler.setThreadNamePrefix("ws-presence-");
    this.ownedScheduler.setRemoveOnCancelPolicy(true);
    this.ownedScheduler.initialize();
    this.scheduler = ownedScheduler;
  }

  WebSocketPresenceListener(MatchmakingService matchmakingService, TaskScheduler scheduler) {
    this.matchmakingService = matchmakingService;
    this.scheduler = scheduler;
    this.ownedScheduler = null;
  }

  @PreDestroy
  void shutdown() {
    if (ownedScheduler != null) {
      ownedScheduler.shutdown();
    }
  }

  @EventListener
  public void onConnected(SessionConnectedEvent event) {
    Principal user = event.getUser();
    String sessionId = sessionIdOf(event.getMessage().getHeaders());
    if (user == null || sessionId == null) {
      return;
    }
    String userId = user.getName();
    sessionsByUser.compute(
        userId,
        (k, sessions) -> {
          Set<String> updated = sessions == null ? new HashSet<>() : new HashSet<>(sessions);
          updated.add(sessionId);
          return updated;
        });
    ScheduledFuture<?> pending = pendingReleases.remove(userId);
    if (pending != null) {
      pending.cancel(false);
      log.info("User {} reconnected within the grace period, keeping their chat", userId);
    }
  }

  @EventListener
  public void onDisconnect(SessionDisconnectEvent event) {
    Principal user = event.getUser();
    String sessionId = event.getSessionId();
    if (user == null || sessionId == null) {
      return;
    }
    String userId = user.getName();
    boolean[] lastSessionClosed = {false};
    sessionsByUser.computeIfPresent(
        userId,
        (k, sessions) -> {
          Set<String> updated = new HashSet<>(sessions);
          updated.remove(sessionId);
          if (updated.isEmpty()) {
            lastSessionClosed[0] = true;
            return null;
          }
          return updated;
        });
    if (!lastSessionClosed[0]) {
      return;
    }
    log.info("Last websocket session of user {} closed, releasing in {}", userId, GRACE_PERIOD);
    ScheduledFuture<?> future =
        scheduler.schedule(() -> release(userId), Instant.now().plus(GRACE_PERIOD));
    ScheduledFuture<?> previous = pendingReleases.put(userId, future);
    if (previous != null) {
      previous.cancel(false);
    }
  }

  private void release(String userId) {
    pendingReleases.remove(userId);
    if (sessionsByUser.containsKey(userId)) {
      return;
    }
    try {
      matchmakingService.endChat(userId);
    } catch (RuntimeException e) {
      log.error("Failed to release disconnected user {}", userId, e);
    }
  }

  /** Number of open websocket sessions of a user. */
  int openSessions(String userId) {
    Set<String> sessions = sessionsByUser.get(userId);
    return sessions == null ? 0 : sessions.size();
  }

  private static String sessionIdOf(org.springframework.messaging.MessageHeaders headers) {
    Object id = headers.get("simpSessionId");
    return id == null ? null : id.toString();
  }
}
