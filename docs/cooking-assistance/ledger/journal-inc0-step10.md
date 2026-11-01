# Journal — increment 0, step 10 (Outbound messaging)

## Slice check (decisions 2026-11-01)
- Publish decisions: HelpRequested → publishHelpRequestCreated, HelpProvided → publishHelpProvided; the exhaustive switch over the sealed HelpRequestEvent in OutboxEventMapper is the gate. Accepted/rejected/cancelled/timed-out → increment 1.
- Payloads move to the generated records (step 7 r2); messageId = UUID v3 of "cooking-assistance-outbox-<id>" (MessageHeader requires a uuid); correlationId = helpRequestId until L8 (step 12); relay scope = skeleton + concurrency; retry/backoff/dead letters → increment 1.

## Step 7 r2
- OutboxEventMapperTest (2 guards: exact field set, no nulls): green on activation; trial without @JsonInclude(NON_NULL) → item 2 fails on helpProvider, as predicted.
- Refactor to generated records + NON_NULL in AsyncApiJson; guards and clean verify green. Commits 6035a70 (test), 1367314 (refactor).

## Test lists
- Mapper 7, relay 6 (+R4b added on the way, approved), wiring 1; verify green with 7 + 7 skipped as predicted.

## Cycles
- Preparation: AsyncApiContract moved to adapter.messaging (shared); fast suite green.
- M1: Red "but was null"; Green channel lookup in the spec + v3 message id + headers. M2/M3 activated together in one edit (process slip); both green; trials: hard-coded channel → only M2; random id → M1, M2, M3 fail (**mismatch: predicted M1, M3 — M2 also asserts its literal id**).
- M4: Red "Expecting code to raise a throwable"; Green send-side validation (MessageHeader + payload schema from the spec). M5 green; trial formats off → M5 and E4 fail (**mismatch: E4 fails as RETRY since E9, predicted an error**). M6: Red IllegalArgumentException → Optional lookups + UnmappedEvent. M7: Red all six missing → registry built + planned; gate trial (made-up send operation) fails as predicted.
- R1: fixture slip first (heredoc wrote \\ line ends → invalid JSON in OutboxPayloads; mapper tests broke; fixed before the Red); Red "Expected size: 1 but was: 0"; Green claim, publish, mark in one transaction. R2 green; trial without published_at filter → "1 but was 2". R3: Red "confirm lost" propagates; Green failed publish leaves the row pending. R4: Red [A, B]; Green held-back aggregates.
- R4b added (gap found in the R4 review: a row appended while an earlier one is in flight at another relay could overtake): Red A2 published by relay 2; Green oldest pending row per aggregate (NOT EXISTS). Held-back set temporarily dead — kept (approved).
- R5: Red A2 missing; Green claim rounds per transaction, held-back across the run. Held-back trial → only R4 fails, as predicted (named on a rerun).
- R6: green; trial without the row lock → duplicates (10 → 20) as predicted, **mismatch: R4b failed too** (also depends on the lock).
- W1: green; trial event type "HelpRequested" → W1, HelpRequestRepositoryIT (2 tests), ApplicationWiringIT, InboundWiringIT G1 fail (**predicted one of the two repository tests only**).
- Finish: clean verify ×2 green (115 fast + 40 slow); publish-decision gate trial (made-up event in the sealed union) → compile error in OutboxEventMapper; leak grep none; 5 ifs, all delivery/contract decisions.
- Slice refactoring (one agent for both slices): kept checkAgainstSpec + headersOf; tests unchanged; clean verify ×2 green. Step 10 approved 2026-11-01.
