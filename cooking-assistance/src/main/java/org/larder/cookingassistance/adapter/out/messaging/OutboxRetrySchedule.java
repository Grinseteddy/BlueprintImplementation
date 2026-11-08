package org.larder.cookingassistance.adapter.out.messaging;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;

/** ADR0015: when a row that failed for a broker reason goes out again. */
class OutboxRetrySchedule {

  private final JdbcClient jdbc;

  OutboxRetrySchedule(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** ADR0015 L5: a broker error is retried later, never dead-lettered. */
  void afterBrokerError(OutboxRecord row, RuntimeException error, OffsetDateTime now) {
    int failures =
        jdbc.sql("select attempts from outbox where id = :id")
                .param("id", row.id())
                .query(Integer.class)
                .single()
            + 1;
    jdbc.sql(
            "update outbox set attempts = :failures, last_error = :error,"
                + " next_attempt_at = :nextAttemptAt where id = :id")
        .param("failures", failures)
        .param("error", error.getMessage())
        .param("nextAttemptAt", now.plus(backoffAfter(failures)))
        .param("id", row.id())
        .update();
  }

  /**
   * ADR0015 addition (step 13, A-2): the broker is back, so rows waiting after a broker error
   * become due at once.
   */
  void endBackoff(OffsetDateTime now) {
    jdbc.sql(
            "update outbox set next_attempt_at = :now where "
                + OutboxRelay.PENDING
                + " and attempts > 0 and next_attempt_at > :now")
        .param("now", now)
        .update();
  }

  /** ADR0015 L5: 1 s, doubling per consecutive failure, at most 60 s. */
  private static Duration backoffAfter(int failures) {
    return Duration.ofSeconds(Math.min(60, 1L << Math.min(failures - 1, 6)));
  }
}
