package org.larder.cookingassistance.adapter.out.messaging;

import java.util.UUID;

/**
 * An outbox row as the relay reads it: the event under its AsyncAPI message name, and the trace
 * context of the command that wrote it (null without a trace).
 */
record OutboxRecord(
    long id, UUID aggregateId, String eventType, String payload, String traceparent) {

  /** A row written without a trace. */
  OutboxRecord(long id, UUID aggregateId, String eventType, String payload) {
    this(id, aggregateId, eventType, payload, null);
  }
}
