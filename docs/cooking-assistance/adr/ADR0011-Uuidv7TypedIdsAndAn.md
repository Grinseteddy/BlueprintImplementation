ADR0011

# UUIDv7 typed IDs and an injected clock

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will generate UUIDv7 identifiers in the application, wrap them in typed IDs in the domain, inject `java.time.Clock`, and store times as UTC `timestamptz`.**

## Context
Deadlines (12 h, 24 h, 15/5 min, 48 h) must be testable without waiting. IDs must exist before the outbox row is written.

## Options considered

### UUIDv7 in the application + Clock

time-ordered keys, deterministic tests

### Database-generated IDs

the ID is unknown until insert

## Consequences

- Tests use a fixed or stepping clock

## Principles
AP0005

## Quality scenarios
QS-01, QS-09
