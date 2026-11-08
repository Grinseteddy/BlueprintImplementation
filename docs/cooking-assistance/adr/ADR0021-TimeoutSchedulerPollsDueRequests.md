ADR0021

# Timeout scheduler polls due help requests with SKIP LOCKED

### Adopted 2026-11-03

### Annegret Junker, Architect

## Decision
**We will time out help requests with a poller per instance (`@Scheduled(fixedDelay)`, every 10 s): in one transaction it claims Open help requests whose every asked provider's deadline has passed with `FOR UPDATE SKIP LOCKED` (in batches), calls `timeOut` on each and saves it with its outbox row. The domain stays authoritative for the rule (INV-09, step 3 r5); the claim query only selects candidates. Optimistic locking (ADR0010) is the second guard against a double time-out.**

## Context
B3-S06 "Time out help request" is triggered by the clock (design brief §4, policy Timeout). QS-09: the time-out fires at most 30 s after the deadline, exactly once per deadline, also with 2 instances. Open question L4 from step 5, answered at step 8 of increment 1 by the user.

## Options considered

### DB-polled deadlines with `FOR UPDATE SKIP LOCKED`

the same pattern as the outbox relay (ADR0015); no new dependency; instances never wait on each other; testable against the real database

### ShedLock

one instance polls at a time; a further dependency and a lock table

### Quartz cluster

persistent jobs per deadline; many tables and its own clustering for one rule

## Consequences

- The repository gets one finder for due requests, built test-first in step 7 (revision from step 8, increment 1)
- The claim query repeats the deadline boundary ("the deadline itself still counts") in SQL; a disagreement with the domain shows as `DeadlineNotPassed` and is a defect
- Poll interval 10 s keeps QS-09's 30 s with margin; the interval is configuration
- Deadlines per type (B2 24 h, B4 15/5 min, B1 48 h) use the same poller

## Principles
AP0002 (honors), ADR0010, ADR0015

## Quality scenarios
QS-09, QS-10

## Addition (step 5 revision from step 13, increment 1, 2026-11-08, decided by the user)

- **Each candidate runs in its own savepoint** (inspection A-5). A failing candidate is logged at ERROR with its id and reason and undone, and the batch goes on.
- **The poller claims again in the same poll only after a full batch** (`shouldClaimAgainAfter`, QS-09). A run with a failing candidate times out fewer than a batch, so it ends the drain even when more are due; the next poll claims those, and the failed candidate again.
