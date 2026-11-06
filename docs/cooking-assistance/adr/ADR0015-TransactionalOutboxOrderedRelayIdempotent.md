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

## Addition (step 5 revision from step 14, increment 0, 2026-11-01, decided by the user)

- **Dead letters go through the module's own exchange** `cooking-assistance.dlx` (direct), bound to `cooking-assistance.dlq`. The default exchange would need write rights on `amq.default`, which would let the module's broker user publish to any queue by name (security review SEC-04, ADR0012 addition).

## Addition (later question L5, step 10 of increment 1, 2026-11-04, decided by the user)

- **The relay never dead-letters for broker errors.** A failed publish or a lost confirm leaves the row pending: `attempts` + 1, `last_error`, `next_attempt_at` = now + backoff (1 s, doubling, capped at 60 s), from the injected clock. A row is not claimed before its `next_attempt_at` (QS-04: nothing is lost while the broker is down).
- **Rows that can never succeed are dead-lettered at once:** a message that breaks the spec, or an event type the spec does not know. `dead_lettered_at` and `last_error` are set; nothing is sent.
- **A dead-lettered row unblocks its help request:** the later rows go out; the dead letter is logged at ERROR with row id, event type and reason (dead letters come only from defects).
- **Replay by runbook for now:** after the cause is fixed, an operator resets `dead_lettered_at`, `attempts` and `next_attempt_at` of the row (README); the stable message ID keeps a replay safe for consumers. A replay tool is on the planned list.
