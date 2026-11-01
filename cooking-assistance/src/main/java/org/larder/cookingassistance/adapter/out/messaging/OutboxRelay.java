package org.larder.cookingassistance.adapter.out.messaging;

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
  private final OutboxMessageMapper mapper = new OutboxMessageMapper();

  public OutboxRelay(
      JdbcClient jdbc, TransactionTemplate transactions, MessagePublisher publisher) {
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.publisher = publisher;
  }

  /** Claim rounds until a round makes no progress: an aggregate drains in one run. */
  public void runOnce() {
    Set<UUID> heldBack = new HashSet<>();
    for (int round = 0; round < MAX_ROUNDS; round++) {
      if (!Boolean.TRUE.equals(transactions.execute(status -> publishRound(heldBack)))) {
        return;
      }
    }
  }

  /** One transaction: the oldest pending row of each help request; true if any was published. */
  private boolean publishRound(Set<UUID> heldBack) {
    boolean published = false;
    for (OutboxRecord row : claimPending()) {
      // ADR0015: in order per help request; a pending row holds back its aggregate for the run
      if (heldBack.contains(row.aggregateId())) {
        continue;
      }
      try {
        publisher.publish(mapper.toMessage(row));
        markPublished(row);
        published = true;
      } catch (RuntimeException notConfirmed) {
        // ADR0015: at least once; the row stays pending and goes out again, same message id
        LOG.warn("outbox row {} not published", row.id(), notConfirmed);
        heldBack.add(row.aggregateId());
      }
    }
    return published;
  }

  private List<OutboxRecord> claimPending() {
    return jdbc.sql(
            "select id, aggregate_id, event_type, payload::text as payload from outbox"
                + " where published_at is null and dead_lettered_at is null"
                // ADR0015: only the oldest pending row of each help request, also against rows
                // another relay holds
                + " and not exists (select 1 from outbox earlier"
                + " where earlier.aggregate_id = outbox.aggregate_id and earlier.id < outbox.id"
                + " and earlier.published_at is null and earlier.dead_lettered_at is null)"
                + " order by id for update skip locked")
        .query(OutboxRecord.class)
        .list();
  }

  private void markPublished(OutboxRecord row) {
    jdbc.sql("update outbox set published_at = now() where id = :id")
        .param("id", row.id())
        .update();
  }
}
