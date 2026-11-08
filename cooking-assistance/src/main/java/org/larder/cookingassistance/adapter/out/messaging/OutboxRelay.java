package org.larder.cookingassistance.adapter.out.messaging;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivers the outbox to the broker: at least once, stable message ids, in order per aggregate. */
public class OutboxRelay {

  private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ROUNDS = 100;

  /** ADR0015: a row is pending until it is published or dead-lettered. */
  static final String PENDING = "published_at is null and dead_lettered_at is null";

  private final JdbcClient jdbc;
  private final TransactionTemplate transactions;
  private final MessagePublisher publisher;
  private final Clock clock;
  private final OutboxMessageMapper mapper = new OutboxMessageMapper();
  private final OutboxRetrySchedule retries;

  public OutboxRelay(
      JdbcClient jdbc, TransactionTemplate transactions, MessagePublisher publisher, Clock clock) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.publisher = publisher;
    this.clock = clock;
    this.retries = new OutboxRetrySchedule(jdbc);
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

  /** ADR0015 addition (step 13, A-2): the broker's return ends the wait of the failed rows. */
  public void brokerReconnected() {
    retries.endBackoff(now());
  }

  /**
   * One transaction over the oldest pending row of each help request; true if the round made
   * progress, i.e. a row was published or dead-lettered.
   */
  private boolean relayRound(Set<UUID> heldBack) {
    boolean progressed = false;
    for (OutboxRecord row : claimPending()) {
      // ADR0020, QS-14: the row's help request is on the relay's log lines
      try (MDC.MDCCloseable context =
          MDC.putCloseable("helpRequestId", row.aggregateId().toString())) {
        progressed |= deliver(row, heldBack);
      }
    }
    return progressed;
  }

  /** True if the row left the pending rows, i.e. it was published or dead-lettered. */
  private boolean deliver(OutboxRecord row, Set<UUID> heldBack) {
    // ADR0015: in order per help request; a pending row holds back its aggregate for the run
    if (heldBack.contains(row.aggregateId())) {
      return false;
    }
    OutgoingMessage message;
    try {
      message = mapper.toMessage(row);
    } catch (InvalidOutgoingMessage | UnmappedEvent unpublishable) {
      deadLetter(row, unpublishable);
      return true;
    }
    try {
      publisher.publish(message);
      markPublished(row);
      return true;
    } catch (RuntimeException notConfirmed) {
      // ADR0015: at least once; the row stays pending and goes out again, same message id
      LOG.warn("outbox row {} not published", row.id(), notConfirmed);
      retries.afterBrokerError(row, notConfirmed, now());
      heldBack.add(row.aggregateId());
      return false;
    }
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

  private OffsetDateTime now() {
    return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
  }

  private List<OutboxRecord> claimPending() {
    return jdbc.sql(
            "select id, aggregate_id, event_type, payload::text as payload, traceparent"
                + " from outbox"
                + " where "
                + PENDING
                + " and next_attempt_at <= :now"
                // ADR0015: only the oldest pending row of each help request, also against rows
                // another relay holds
                + " and not exists (select 1 from outbox earlier"
                + " where earlier.aggregate_id = outbox.aggregate_id and earlier.id < outbox.id"
                + " and "
                + PENDING
                + ")"
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
