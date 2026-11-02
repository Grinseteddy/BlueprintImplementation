package org.larder.cookingassistance.adapter.out.messaging;

import java.util.UUID;

/** An outbox row as the relay reads it: the event under its AsyncAPI message name. */
record OutboxRecord(long id, UUID aggregateId, String eventType, String payload) {}
