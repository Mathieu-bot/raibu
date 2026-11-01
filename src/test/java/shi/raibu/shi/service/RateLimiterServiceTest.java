package shi.raibu.shi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimiterServiceTest {

  private static final long WINDOW = 60_000L;

  @Test
  void blocks_after_the_limit_and_reopens_in_the_next_window() {
    RateLimiterService service = new RateLimiterService();

    assertTrue(service.isAllowed("IP:1", 2, WINDOW, 0));
    assertTrue(service.isAllowed("IP:1", 2, WINDOW, 1));
    assertFalse(service.isAllowed("IP:1", 2, WINDOW, 2));
    assertTrue(service.isAllowed("IP:1", 2, WINDOW, WINDOW + 1));
  }

  @Test
  void keys_are_counted_independently() {
    RateLimiterService service = new RateLimiterService();

    assertTrue(service.isAllowed("IP:1", 1, WINDOW, 0));
    assertFalse(service.isAllowed("IP:1", 1, WINDOW, 1));
    assertTrue(service.isAllowed("IP:2", 1, WINDOW, 1));
  }

  @Test
  void expired_windows_are_purged_so_memory_stays_bounded() {
    RateLimiterService service = new RateLimiterService();
    for (int i = 0; i <= RateLimiterService.PURGE_THRESHOLD; i++) {
      service.isAllowed("IP:" + i, 5, WINDOW, 0);
    }
    assertTrue(service.trackedKeys() > RateLimiterService.PURGE_THRESHOLD);

    service.isAllowed("IP:new", 5, WINDOW, WINDOW + 1);

    assertEquals(1, service.trackedKeys());
  }

  @Test
  void never_lets_more_than_the_limit_through_under_concurrency() throws Exception {
    RateLimiterService service = new RateLimiterService();
    AtomicInteger allowed = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    for (int i = 0; i < 200; i++) {
      pool.submit(
          () -> {
            if (service.isAllowed("IP:shared", 50, WINDOW, 0)) {
              allowed.incrementAndGet();
            }
          });
    }
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    assertEquals(50, allowed.get());
  }
}
