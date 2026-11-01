package shi.raibu.shi.service;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Fixed-window request counter kept in memory, so limits apply per application instance. Expired
 * windows are purged once the map grows past {@link #PURGE_THRESHOLD}, which keeps the memory use
 * proportional to the number of distinct clients seen during one window.
 */
@Service
public class RateLimiterService {

  static final int PURGE_THRESHOLD = 10_000;

  private static final class Window {
    final long windowStartMs;
    final long windowMs;
    int count;

    Window(long windowStartMs, long windowMs, int count) {
      this.windowStartMs = windowStartMs;
      this.windowMs = windowMs;
      this.count = count;
    }

    boolean isExpired(long now) {
      return now - windowStartMs > windowMs;
    }
  }

  private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

  public boolean isAllowed(String key, int limit, long windowMs) {
    return isAllowed(key, limit, windowMs, System.currentTimeMillis());
  }

  boolean isAllowed(String key, int limit, long windowMs, long now) {
    boolean[] allowed = new boolean[1];
    windows.compute(
        key,
        (k, window) -> {
          if (window == null || window.isExpired(now)) {
            allowed[0] = true;
            return new Window(now, windowMs, 1);
          }
          if (window.count >= limit) {
            allowed[0] = false;
            return window;
          }
          window.count++;
          allowed[0] = true;
          return window;
        });

    if (windows.size() > PURGE_THRESHOLD) {
      windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }
    return allowed[0];
  }

  int trackedKeys() {
    return windows.size();
  }
}
