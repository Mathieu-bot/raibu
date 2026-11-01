package shi.raibu.shi.endpoint.rest.controller;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Shared rules for the optional {@code ?limit=} parameter of list endpoints. */
public final class PageLimits {

  public static final int DEFAULT_LIMIT = 50;
  public static final int MAX_LIMIT = 100;

  private PageLimits() {}

  /** Null or non-positive limits fall back to the default; values above the maximum are capped. */
  public static int clamp(Integer limit) {
    if (limit == null || limit < 1) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  /** The newest {@code limit} rows first, sorted by the given property. */
  public static Pageable newestFirst(Integer limit, String property) {
    return PageRequest.of(0, clamp(limit), Sort.by(Sort.Direction.DESC, property));
  }
}
