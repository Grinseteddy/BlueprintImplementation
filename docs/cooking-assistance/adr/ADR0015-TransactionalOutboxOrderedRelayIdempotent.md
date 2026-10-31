ADR0015

# Transactional outbox, ordered relay, idempotent consumer

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will publish with Spring AMQP through a transactional outbox: events are written in the aggregate's transaction; a relay per instance claims rows with `FOR UPDATE SKIP LOCKED`, publishes in order per help request with publisher confirms and exponential backoff (1 s → 60 s). Consumers record `messageId` in `processed_message` in the same transaction, acknowledge manually, and dead-letter to `cooking-assistance.dlq` after 5 deliveries.**

## Context
QS-04: no event is lost while the broker is down. QS-08: ≤ 5 s to the broker. The skeleton publishes `HelpRequestCreated` and `HelpProvided` and consumes `GrandmaHelpProvided`.

## Options considered

### Own outbox + relay

explicit, testable guarantees

### Spring Modulith event publication registry

less code; less control over order and backlog metrics

### Publish after commit

events can be lost (violates QS-04)

## Consequences

- Outbox backlog and age are exposed as metrics (QS-14)
- Replay from the DLQ is decided in L5

## Principles
AP0002 (honors), ADR0003

## Quality scenarios
QS-04, QS-08, QS-10, QS-14
