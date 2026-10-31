package org.larder.cookingassistance.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The transactional outbox (ADR0015): appends events in the caller's transaction. Only the event
 * columns are written; the delivery columns keep their defaults for the relay (step 10).
 */
@Component
class Outbox {

  private final JdbcClient jdbc;

  Outbox(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void append(String aggregateType, UUID aggregateId, OutboxEvent event) {
    jdbc.sql(
            "insert into outbox (aggregate_type, aggregate_id, event_type, payload, occurred_at)"
                + " values (:aggregateType, :aggregateId, :eventType, cast(:payload as jsonb),"
                + " :occurredAt)")
        .param("aggregateType", aggregateType)
        .param("aggregateId", aggregateId)
        .param("eventType", event.eventType())
        .param("payload", event.payload())
        .param("occurredAt", OffsetDateTime.ofInstant(event.occurredAt(), ZoneOffset.UTC))
        .update();
  }
}
