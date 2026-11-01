package shi.raibu.shi.endpoint.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

class PageLimitsTest {

  @Test
  void defaultsToFiftyWhenMissingOrInvalid() {
    assertEquals(50, PageLimits.clamp(null));
    assertEquals(50, PageLimits.clamp(0));
    assertEquals(50, PageLimits.clamp(-3));
  }

  @Test
  void capsAtOneHundred() {
    assertEquals(100, PageLimits.clamp(100));
    assertEquals(100, PageLimits.clamp(5000));
    assertEquals(7, PageLimits.clamp(7));
  }

  @Test
  void sortsNewestFirst() {
    var pageable = PageLimits.newestFirst(20, "sentAt");
    assertEquals(20, pageable.getPageSize());
    assertEquals(Sort.Direction.DESC, pageable.getSort().getOrderFor("sentAt").getDirection());
  }
}
