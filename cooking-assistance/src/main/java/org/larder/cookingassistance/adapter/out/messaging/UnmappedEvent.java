package org.larder.cookingassistance.adapter.out.messaging;

/** An outbox row whose event type no message of the spec carries; never published silently. */
final class UnmappedEvent extends RuntimeException {

  UnmappedEvent(String eventType) {
    super("no message of the AsyncAPI spec carries " + eventType);
  }
}
