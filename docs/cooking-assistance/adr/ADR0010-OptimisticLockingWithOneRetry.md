ADR0010

# Optimistic locking with one retry

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will protect the Help Request aggregate with optimistic locking (`version` column); the application service retries a conflicting command once.**

## Context
QS-10: the Community and the Grandma Avatar may answer the same request at the same time; no answer may be lost.

## Options considered

### Optimistic lock + one retry

no lock held across requests; a conflict reloads the aggregate and re-applies the command

### Pessimistic lock

serializes all writers; risks waits

## Consequences

- A second conflict answers 409 CONCURRENT_UPDATE (REST) or redelivers (messaging) *(corrected 2026-11-01, reverse check of increment 0: INVALID_STATE is the lifecycle rejection)*

## Principles
—

## Quality scenarios
QS-10
