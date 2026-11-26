# Journal — increment 3, step 8 (Application services)

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01, B4-S02, plus B3-S05/S06 as reused for US-CA-04. EXACT Coding at 88fb3f9, autonomy full-hitl. Inputs: reuse steps 3, 5, 6, 7 (user, 2026-11-14). Fakes: `InMemoryHelpRequestRepository` passes the shared contract (step 7: 17/17 on the fake and on JDBC).

## Slice check
- B4-S01 Request help Catastrophe → new use case `RequestHelpWithCatastrophe` → `HelpRequest.requestHelpWithCatastrophe`; ADR0013 role cook; every cook can ask (INV-18, R1.E3 no plan check).
- B4-S02 Provide Help (mitigation) → existing `ProvideHelp` (Community) and `ProvideGrandmaAvatarHelp` (policy handler, ADR0015) via the `HelpAnswer` overload; no new code expected.
- B3-S06 per side (r21) → existing `TimeOutDueHelpRequests`; a side-only pass saves with no event and counts as handled (no re-claim: the side is no longer ASKED).
- B3-S05 reject (INV-23, +5 min Grandma) → existing `RejectHelp`; B3-S03 cancel, B3-S04 accept: independent of the kind of help, not listed.
- INV-21 Community notification → Notification context (other owner); INV-24 notes → step 11. No policy of this context in B4.

## Test list
- 16 items: `RequestHelpWithCatastropheTest` 8 (new), `ProvideHelpTest` 2, `ProvideGrandmaAvatarHelpTest` 2, `TimeOutDueHelpRequestsTest` 3, `RejectHelpTest` 1. Inactive list compiles; fast suite green, 16 skipped, as predicted.
- Test list approved by the user 2026-11-14.

## Cycles
- #1 Red (compile: `RequestHelpWithCatastrophe` does not exist), as predicted.
- #1 Green: `RequestHelpWithCatastrophe` (load-free create → save → id; role check left to #8); #1 and fast suite green, as predicted.
- #2 green on activation, as predicted; trial (`pullEvents()` before save) failed #2 only; restored.
- #3 green on activation, as predicted (test data aligned with its name before the run: Tom's souffle); trial (plan check) failed #3 only with NotAuthorized; restored.
- #4 green on activation, as predicted; trial (use case swallows DomainRejection) failed #4 only; restored.
- #5 green on activation, as predicted; trial (swallow) failed #4 and #5; restored.
- #6 green on activation, as predicted; trial (swallow) failed #4–#6; restored.
- #7 green on activation, as predicted; trial (swallow) failed #4–#7; restored.
- #8 Red (nothing thrown: no role check yet), as predicted.
- #8 Green: `actor().require(Role.COOK, …)` first (ADR0013); file 8/8, fast suite green, as predicted.
- #9 green on activation, as predicted; trial (domain dispatch rejects mitigations) failed #9 only; restored.
- #10 green on activation, as predicted; trial (ProvideHelp swallows DomainRejection) failed #10 and the 8 older pass-through tests, nothing else; restored.
- #11 green on activation, as predicted; trial (domain dispatch rejects mitigations) failed #11 only; restored.
- #12 green on activation, as predicted (rule INV-09 of AnswerTooLate, the shared deadline guard); trial (handler swallows DomainRejection) failed #12 and the 2 older propagation tests; restored.
- #13 green on activation, as predicted; trial (a candidate still OPEN after `timeOut` fails in its savepoint, i.e. is not saved) failed #13 only (0 returned); restored.
- #14 green on activation, as predicted; trial (domain never times out the whole request) failed #14 and the 4 older CANCELLED tests, #13 green; restored.
- #15 green on activation, as predicted; trial (as #14) failed #15, #14 and the 4 older CANCELLED tests; restored.
- #16 green on activation, as predicted; trial (RejectHelp clock + 1 min) **mismatch**: failed #16 and lenaRejectsGrandmasHelpWithANote as predicted, and also anotherWriterFirstRetriesOnce, which I did not predict — its expectations name Grandma's new deadline too ("asked again until …"). Restored. Stopped for the user.

## Refactoring
- Isolated refactor agent (one, after #16): no trial, no change. The three RequestHelpWith* use cases share an incidental shape, not knowledge (access per command, ADR0013; different factories); no `if` in `RequestHelpWithCatastrophe` (the role check lives in `Actor.require`). Tests unchanged (`diff -r` against the snapshot: empty); production unchanged.

## Wiring
- `ApplicationWiringIT#requestHelpWithCatastropheRunsThroughTheRealAdapter` Red (`NoSuchBeanDefinitionException`), the other wiring tests green, as predicted.
- Wiring Green: bean `requestHelpWithCatastrophe` in `ApplicationConfiguration`; ApplicationWiringIT 8/8, fast suite green, as predicted.

## Finish
- 16/16 active and green, plus the wiring test (ApplicationWiringIT 8/8). Shared repository contract green on the fake and on JDBC (step 7).
- Prediction mismatch, behavioral: 1 (trial of #16 also failed `anotherWriterFirstRetriesOnce`, whose expectations name Grandma's new deadline). No process slip.
- Checks: no `if` in `RequestHelpWithCatastrophe`; no infrastructure import in the application layer (grep: none).
- `./mvnw -o verify` ×2 2026-11-14 (time-out pass, ADR0021): BUILD SUCCESS both times (fast 564, slow 200 + larder-app), as predicted.
- Commands → use cases: Request help Catastrophe → `RequestHelpWithCatastrophe` (new); Provide Help (mitigation) → `ProvideHelp`, `ProvideGrandmaAvatarHelp` (unchanged); Time out help request per side → `TimeOutDueHelpRequests` (unchanged); Reject help → `RejectHelp` (unchanged). Access ADR0013 (cook; no plan check, INV-18); idempotency ADR0015 and retry ADR0010 unchanged.

## Handed on
- Step 9: REST `createHelpRequest` for STEPS_TO_MITIGATE_CATASTROPHE → `RequestHelpWithCatastrophe.Command` (optional recipe, pictures); `provideHelp` body and Grandma message with a CatastropheMitigation → `HelpAnswer`; AnswerTooLate from Grandma → ignored with ACK (Q-A2) *(corrected 2026-11-14 in step 9, user: was "dead-letter with its rule")*.
- Step 11: `HelpMapper` placeholder for catastrophe answers; pictures on the read side; INV-24 notes by recipe.
