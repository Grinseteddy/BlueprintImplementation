package org.larder.cookingassistance.adapter.out.messaging;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivers the outbox to the broker: at least once, stable message ids, in order per aggregate. */
public class OutboxRelay {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ROUNDS = 100;

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final MessagePublisher publisher;
  private final Clock clock;
  private final OutboxMessageMapper mapper = new OutboxMessageMapper();

  public OutboxRelay(
      JdbcClient jdbc, TransactionTemplate transactions, MessagePublisher publisher, Clock clock) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.publisher = publisher;
    this.clock = clock;
  }

  /**
   * Claim rounds until a round makes no progress (nothing published or dead-lettered): an aggregate
   * drains in one run.
   */
  public void runOnce() {
    Set<UUID> heldBack = new HashSet<>();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      if (!Boolean.TRUE.equals(transactions.execute(status -> relayRound(heldBack)))) {
        return;
      }
    }
  }

  /**
   * One transaction over the oldest pending row of each help request; true if the round made
   * progress, i.e. a row was published or dead-lettered.
   */
  private boolean relayRound(Set<UUID> heldBack) {
    boolean progressed = false;
    for (OutboxRecord row : claimPending()) {
      // ADR0015: in order per help request; a pending row holds back its aggregate for the run
      if (heldBack.contains(row.aggregateId())) {
        continue;
      }
      OutgoingMessage message;
      try {
        message = mapper.toMessage(row);
      } catch (InvalidOutgoingMessage | UnmappedEvent unpublishable) {
        deadLetter(row, unpublishable);
        progressed = true;
        continue;
      }
      try {
        publisher.publish(message);
        markPublished(row);
        progressed = true;
      } catch (RuntimeException notConfirmed) {
        // ADR0015: at least once; the row stays pending and goes out again, same message id
        LOG.warn("outbox row {} not published", row.id(), notConfirmed);
        scheduleNextAttempt(row, notConfirmed);
        heldBack.add(row.aggregateId());
      }
    }
    return progressed;
  }

  /** ADR0015 L5: retrying cannot fix a row that breaks the spec; it is dead-lettered at once. */
  private void deadLetter(OutboxRecord row, RuntimeException reason) {
    LOG.error(
        "outbox row {} ({}) dead-lettered: {}", row.id(), row.eventType(), reason.getMessage());
    jdbc.sql("update outbox set dead_lettered_at = :now, last_error = :error where id = :id")
        .param("now", now())
        .param("error", reason.getMessage())
        .param("id", row.id())
        .update();
  }

  /** ADR0015 L5: a broker error is retried later, never dead-lettered. */
  private void scheduleNextAttempt(OutboxRecord row, RuntimeException error) {
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
        .param("nextAttemptAt", now().plus(backoffAfter(failures)))
        .param("id", row.id())
        .update();
  }

  /** ADR0015 L5: 1 s, doubling per consecutive failure, at most 60 s. */
  private static Duration backoffAfter(int failures) {
    return Duration.ofSeconds(Math.min(60, 1L << Math.min(failures - 1, 6)));
  }

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private List<OutboxRecord> claimPending() {
    return jdbc.sql(
            "select id, aggregate_id, event_type, payload::text as payload from outbox"
                + " where published_at is null and dead_lettered_at is null"
                + " and next_attempt_at <= :now"
                // ADR0015: only the oldest pending row of each help request, also against rows
                // another relay holds
                + " and not exists (select 1 from outbox earlier"
                + " where earlier.aggregate_id = outbox.aggregate_id and earlier.id < outbox.id"
                + " and earlier.published_at is null and earlier.dead_lettered_at is null)"
                + " order by id for update skip locked")
        .param("now", now())
        .query(OutboxRecord.class)
        .list();
  }

  private void markPublished(OutboxRecord row) {
    jdbc.sql("update outbox set published_at = :now where id = :id")
        .param("now", now())
        .param("id", row.id())
        .update();
  }
}
