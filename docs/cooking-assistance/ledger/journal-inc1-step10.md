# Journal — increment 1, step 10 (Outbound messaging), the four new send operations and the relay's retry and dead letter

Board B3, US-CA-03, slices B3-S03 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Generators unchanged (Modelina message models for the four payloads exist since step 7 of
increment 1; no spec change). Fast loop: `OutboxMessageMapperTest`; slow: `OutboxRelayIT`,
`OutboundWiringIT` (Testcontainers PostgreSQL 16, fake broker behind the publisher port).

## Slice check (user 2026-11-04)
- HelpRequestCancelled → publishHelpRequestCancelled, HelpAccepted → publishHelpAccepted,
  HelpRejected → publishHelpRejected, HelpRequestTimedOut → publishHelpRequestTimedOut; the outbox
  rows carry the AsyncAPI field names since step 7. The generic mapper (channel and payload schema
  from the spec by event type) needs no new code.
- F1: `SendOperations` listed the four as planned → built (coverage gate M5).
- F2 (defect in waiting): a row that breaks the spec or has an unknown event type was caught like a
  broker error and blocked its help request for ever.
- F3: the relay used SQL `now()`; time comes from the injected clock.
- F4 = later question L5, answered by the user and recorded as the ADR0015 addition: never
  dead-letter for broker errors (backoff 1 s doubling, capped at 60 s, no claim before
  `next_attempt_at`); spec-breaking and unknown rows dead-lettered at once; a dead letter unblocks its
  help request and is logged at ERROR with row id, event type and reason; replay by runbook (README),
  a replay tool on the planned list.

## Test list (approved 2026-11-04)
14 items: OutboxMessageMapperTest M1–M5, OutboxRelayIT R1–R8, OutboundWiringIT W2. Replaced: the
increment-0 coverage item `everySendOperationIsBuiltOrPlanned` (by M5) and the increment-0 relay item
"lost confirm → the next run sends again" (by R2). Added on the way: R9 (user, from the relay
refactor). R5's title said "helpRequestId"; corrected to the spec's field `helpRequest`.

## Mapping (M1–M5)
- M1–M4 (cancelled, accepted, rejected, timed out; rows 44–47, message ids from the row id) green on
  activation; each trial (a wrong channel lookup for that event type) failed exactly that item.
- M5 `everySendOperationIsBuilt`: Red (the four operations missing) as predicted; Green `BUILT` lists
  all six with `// op · evt` traces, `PLANNED_FOR_INCREMENT_1` removed.
- Refactor (isolated agent, tests unchanged): retained 0; boundary trial `MessageHeaders` class green,
  undone (the message id is a header too; the knowledge would be split). User: keep. The agent noted
  that `coverage/inventory-inc0.yaml` still lists the four operations as planned — user: leave for
  step 12/13 (an inventory for increment 1).

## Relay (R1–R9)
- R1: scaffold an injected `Clock` in `OutboxRelay` and `OutboxPolling` (the wiring test autowires the
  Clock bean); Red attempts 0, last_error null, next_attempt_at -infinity as predicted. Green
  `scheduleNextAttempt` (attempts + 1, last_error, now + 1 s) and the claim filters
  `next_attempt_at <= :now`. The increment-0 lost-confirm item contradicted L5 and was removed in this
  Green (R2 replaces it); the user's "continue" was taken as Apply.
- R2: Red `published_at` = the database's wall clock vs 12:00:01 as predicted; Green `markPublished`
  with the clock — no SQL `now()` left in the relay (F3).
- R3 (8 rows): Red rows 2–8 at 12:00:01 as predicted; Green `backoffAfter(failures)` =
  min(60 s, 2^(n−1) s), the shift capped against overflow.
- R4 (twelve failures, never dead-lettered) green on activation; trial (dead letter at the 5th
  failure) failed only R4, attempts 5 vs 12, as predicted.
- R5: Red `dead_lettered_at` null as predicted; Green map before publishing, `InvalidOutgoingMessage`
  → `deadLetter` (ERROR log).
- **R6 — behavioral prediction mismatch.** Predicted an assertion failure (the unknown row retried);
  got an error: `UnmappedEvent` escaped `runOnce` and rolled the run back, B's row unpublished too.
  Cause: R5's Green moved the mapping out of the broker-error `try` and caught only
  `InvalidOutgoingMessage` — a regression R5 introduced and R6 caught. Green
  `catch (InvalidOutgoingMessage | UnmappedEvent)` → `deadLetter`.
- R7: Red A2 not published (a dead letter did not count as progress, the run stopped) as predicted;
  Green the dead letter sets the round's progress flag.
- R8 (a waiting row holds back its help request across runs) green on activation; trial (the
  earlier-row check counts only due rows) failed only R8, A2 overtook, as predicted.
- Refactor (isolated agent, tests unchanged): retained 2 — `published` → `progressed`,
  `publishRound` → `relayRound` with the Javadoc "published or dead-lettered"; `attempts = :failures`
  (one source for the count). Boundary trial `RetryBackoff` class green, undone (the retry policy also
  lives in the write and the claim filter). User: keep both.
- The agent found a drift: ADR0015 L5 asks for the event type in the dead letter's ERROR log; it was
  missing. User: defect, test-first. R9 (`OutputCaptureExtension`): Red one ERROR line without
  `HelpRequestCancelled` as predicted; Green the event type in the log line.

## Wiring (W2)
- W2 (POST create, POST cancellation, one relay run → created then cancelled on their channels, no
  pending rows) green on activation; trial (the controller skips the cancel use case) failed only W2,
  as predicted. The create request moved into a helper shared with W1. No production change, no
  refactor.

## Unit checkpoint
- `mvn -o clean verify`: green — cooking-assistance 281 + 94, larder-app 9 + 29 (12 skipped: the
  increment 1 acceptance scenarios until step 12). OutboxRelayIT and OutboundWiringIT ×2 green.
- Publish-decision gate: a made-up `HelpRequestArchived` in the sealed union failed the build at
  `OutboxEventMapper:44` ("the switch expression does not cover all possible input values"); restored.
- Leaks: no generated, AMQP, Kafka or JDBC type in domain or application (grep empty).
- Every `if`/`catch` in mapper and relay: mapper — violations not empty → `InvalidOutgoingMessage`;
  payload not JSON → `InvalidOutgoingMessage`. Relay — a round without progress ends the run; a held
  back help request is skipped for the rest of the run (order); `InvalidOutgoingMessage |
  UnmappedEvent` → dead letter (L5); any other `RuntimeException` from publish → retry with backoff (L5).
- README: "Replay a dead-lettered outbox row" (find, fix first, release; same message id; the
  replayed message arrives after the help request's later ones).
- Tested with a fake broker only, left for step 12: the AMQP publisher's confirms and routing keys for
  the four new channels against RabbitMQ.

## Summary
- Test items 15/15 active (14 approved + R9 added); behavioral prediction mismatches 1 (R6, explained);
  trial mismatches 0; process slips 1 (R5's title named the wrong field, corrected); trials 8 (M1–M4,
  R4, R8, W2, publish-decision gate); refactor groups 2 (mapping 0 kept, relay 2 kept); sent back: none.
- Planned: a replay tool for dead letters (L5); intent-coverage inventory for increment 1 with the
  four send operations (step 12/13).
