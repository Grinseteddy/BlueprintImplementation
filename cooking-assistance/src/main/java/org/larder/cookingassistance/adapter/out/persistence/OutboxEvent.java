package org.larder.cookingassistance.adapter.out.persistence;

import java.time.Instant;

/** An event as the outbox stores it (ADR0015): AsyncAPI message name, JSON payload, time. */
record OutboxEvent(String eventType, String payload, Instant occurredAt) {}
