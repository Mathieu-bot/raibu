package shi.raibu.shi.endpoint.rest.dto;

import java.time.Instant;

/** A user reported by several distinct people, shown in the admin moderation queue. */
public record FlaggedUserDto(
    String userId, long distinctReporters, long reportCount, Instant lastReportAt) {}
