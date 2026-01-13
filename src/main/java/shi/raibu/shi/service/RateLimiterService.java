package shi.raibu.shi.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

  private static class Window {
    long windowStartMs;
    int count;

    Window(long windowStartMs, int count) {
      this.windowStartMs = windowStartMs;
      this.count = count;
    }
  }

  private final Map<String, Window> windows = new HashMap<>();

  public synchronized boolean isAllowed(String key, int limit, long windowMs) {
    long now = System.currentTimeMillis();
    Window window = windows.get(key);

    if (window == null || now - window.windowStartMs > windowMs) {
      windows.put(key, new Window(now, 1));
      return true;
    }

    if (window.count >= limit) {
      return false;
    }

    window.count++;
    return true;
  }
}
