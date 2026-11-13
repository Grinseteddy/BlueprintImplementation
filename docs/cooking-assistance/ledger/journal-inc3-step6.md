# Journal — increment 3, step 6 (Domain model)

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01 and B4-S02 (B3-S03…S06 reused). EXACT Coding at 88fb3f9, autonomy full-hitl. Branch `feature/cooking-assistance-inc3-catastrophe` from main e9fcdf0.

## Readiness gate and step 3 revisions (user, 2026-11-12)
- Example Mapping current; red card US-CA-04 Q1 answered (no — only a request that asks the Community notifies it); INV-21 is the Notification context's policy, this context's part is HelpRequestCreated with the asked providers.
- Step 3 r20: US-CA-04 R1.E4 reworded (asks chef Marco → refused); Q1 in INV-21; INV-39: after a rejection the other answers are set aside until a new answer arrives (O-7 from step 14 of increment 2). Correction during the gate: increment 1's S3.E2/E3 are about refused answers, not O-7; O-7 follows from INV-06 and the accept guard.
- Step 3 r21: R9.E3 — a time-out pass marks every side whose deadline has passed as timed out, no event of its own, the request is cancelled when every side has; INV-24 — earlier catastrophe notes carry over by the same recipe, none without one; R7.E1 names the recipe.

## Test list
- Approved 2026-11-12: 31 example tests and 4 property tests in `HelpRequestCatastropheTest`; the property placeholders move to `HelpRequestPropertiesTest` (jqwik, fixed seed) when activated.
- Test 4's title says rule INV-01 (INV-18 = INV-01, the shared guard reports INV-01, as on board B2).

## Cycles
- #1 Red (no `requestHelpWithCatastrophe`) → Green: the factory, 5 min for a catastrophe.
- #2 Red (Community 17:45 instead of 17:55) → Green: `answerTimeOf(type, provider)` at request time; rejection still per type (tests 21–22 drive it).
- #3 R1.E3, #4 chef, #5 no provider, #6 title and description, #7 recipe, #8 no recipe, #9 a second request: green on activation, each with a trial that failed only its test (#5's trial also #4, as predicted).
- #10 Red (no `pictures()`) → Green: pictures on the request and in `HelpRequested`; `reconstitute` passes an empty list until step 7 (named scaffold).
- #11 Red (11 photos accepted) → Green: the existing INV-38 guard in the factory; #12 (https) green on activation, trial failed only #12.
- P1 (providers), P2 (photos 0..10), P3 (+15/+5 min): green on activation, trials failed as predicted — except P2's trial, which also failed the earlier board's answer-picture property sharing `MAX_PICTURES` (prediction slip; user: go on).
- #13 Red (no `CatastropheMitigation`) → Green: the record in the sealed `HelpAnswer`, a `provideHelp` overload (provider checks only; recipe and photos come with #14, #16), and explicit placeholders in the exhaustive adapter switches — `AnswerJsonMapper.toJson` (step 7), `HelpMapper.toContractAnswer` (step 11), `AnswerMessageMapper` (step 7/10). Prediction mismatch: the third switch (`AnswerMessageMapper`, fully qualified case labels) was missed by my search; compile error; placeholder added (user); green.

- #14 Red (another recipe accepted) → Green: `CatastropheMitigation.isAbout` (no recipe, or the request's).
- #15 Red (a mitigation on a step request accepted) → Green: `ensureAnswerOfTheRequestedKind` in all three `provideHelp` overloads; found on the way and added to #15: substitutes on a catastrophe request without a recipe ended in an NPE (`requestedRecipe.equals`), now refused (INV-37).
- #16 Red → Green: the INV-38 picture guard for mitigations.
- #17, #18 (per-side answer deadlines), #19, #20 (accept), #21 (Grandma asked again +5 min), #23, #24 (O-7, r20), #25 (cancel), #26, #27, #29, #30, #31 (time-out): green on activation, each trial failed only its test (#29's trial also #26, #27; #27's also #26, as predicted).
- #22 Red (Community asked again 17:52 instead of 18:02) → Green: `askAgain` with each provider's own answer time. P4 green on activation; its first trial edit was void (Spotless had rewrapped the line; the run did not count) and was redone: P4 and #22 failed as predicted.
- #28 Red (`DeadlineNotPassed`) → Green (r21): a time-out pass needs one due side, times out each due side, and cancels the request only when every side has timed out; no event for a single side. The poller's claim query (step 7) must claim a request with one side due.

## Finish
- 31 example tests and 4 properties active and green.
- `verify` green after both refactorings (2026-11-13): 541 + 14 fast, 189 + 69 slow; coverage gate met; Spotless, Error Prone (3 warnings in untouched test files), Spectral green.

## Refactoring (isolated agents, `src/test` unchanged by `diff -r`)
- B4-S01, B4-S02 (kept by the user 2026-11-13): `AnswerTime.of(HelpType, HelpProviderType)` holds the answer times (boundary trial: the answer-time policy changes on its own axis, INV-09/17/26); `AskedProvider.isDueAt(Instant)` replaces the private `HelpRequest.isDueAt`. Spotless rewrapped two comments. Module 541 green, coverage gate met, `spotless:check` green. Prediction slip: Spotless is not part of `test`; checked on its own. Not taken: `HelpAnswer.kind()`, a type for the per-kind fields (`reconstitute`), one deadline helper, `AskedProvider::hasTimedOut`.
- B3-S03…S06 on catastrophe requests (kept by the user 2026-11-13): `timeOutRequest(Instant)` taken out of `timeOut` (boundary trial: the per-side time-out, r21, and the request's time-out — cancelled, `HelpRequestTimedOut`, suggestions — change on separate axes); the comment on `ensureSomeDeadlinePassed` keeps only its own rule. 233 then 541 green as predicted, `spotless:check` green, no reformatting. Not changed: `cancel`, `accept`, `reject`/`askAgain` (one decision each).

## Handed on
- Step 7: catastrophe pictures stored (the `reconstitute` scaffold passes an empty list); `CatastropheMitigation` in `AnswerJsonMapper` and `AnswerMessageMapper` (placeholders); the recipe in `HelpRequestCreated` (INV-19); the poller's claim query must claim a request with one side due (r21).
- Step 9/10: `HelpMapper.toContractAnswer` placeholder (step 11); REST and AMQP for catastrophe requests and mitigations.
- Step 11: INV-24 notes of earlier catastrophe requests with the same recipe (r21).
- Notification context (other owner): INV-21, only requests asking the Community notify it (Q1).
