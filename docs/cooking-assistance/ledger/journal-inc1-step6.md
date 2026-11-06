# Journal — increment 1, step 6 (Domain model), unit Help Request

Board B3 "Help with a preparation step", US-CA-03, slices B3-S02 (extension) to B3-S06.
EXACT Coding at 88fb3f9, autonomy full-hitl. Fast loop: `HelpRequestTest*` and
`HelpRequestPropertiesTest` (jqwik 1.10.2, tries 1000, seed 20261031); slow suite and gates at
the unit checkpoint.

## Before the test list
- Step 3 r5 (approved 2026-11-02, ffd39b2): an answer at or before its deadline counts, a later
  one is `AnswerTooLate` (INV-09); an Open request times out only after every deadline, before
  that `DeadlineNotPassed`; only Open times out; the rejection note is required
  (`RejectionNoteRequired`, INV-06); INV-39 — a rejected Help is never accepted or rejected again
  (`HelpAlreadyRejected`, example S3.E4).
- Test list approved (68d68b2): 32 items; 1 added on the way (per-provider deadline,
  "Add it to the list"), 33 in all.

## B3-S02 extension and B3-S03 Cancel (commit 5f8ce82)
- Late answer refused (`AnswerTooLate`), the per-provider deadline decides; Cancel with optional
  note, `HelpRequestCancelled`, a cancelled request takes no answer.
- Every Red failed for the predicted behavioral reason; every green-on-activation item had a
  trial, each matched.
- Outbox: one placeholder case per new event in `OutboxEventMapper`, throwing
  `UnsupportedOperationException("outbox mapping: step 7, increment 1")` (user decision); step 7
  replaces them test-first.

## B3-S04 Accept and B3-S05 Reject (commit 705e96c)
- Accept closes the request (`HelpAccepted`); Reject needs a note, marks the Help rejected, asks
  every provider again with a new deadline (rejection time + 12 h) and reopens the request
  (`HelpRejected`); INV-39 guards both.
- Step 3 r6 (6bafa9e, approved): INV-05 also names "the Help must belong to the request"
  (`HelpNotInRequest`, NOT_FOUND).
- A4 reworded before activation (user: "Reword and activate").
- **Process slip (activation 7):** the prediction was written after the run. Not counted as
  evidence; the item was checked again with a prediction first.

## B3-S06 Time out
- T1–T3 (R8.E1–E3): silent providers time out, request CANCELLED, sides TIMED_OUT,
  `HelpRequestTimedOut` with timed-out and suggested providers (PREPARATION_STEP_PROVIDERS minus
  those asked). Reds as predicted.
- T4 boundary: time out at exactly the deadline → `DeadlineNotPassed`. As predicted.
- T5 a rejection starts a new deadline: green on activation; trial (askAgain keeps the old
  deadline) failed exactly T5, R1, R2 — as predicted.
- T6 an Answered request does not time out: Red "Expecting code to raise a throwable." as
  predicted; Green `ensureOpen()` first in `timeOut`; 59 passed, 2 skipped as predicted.
- T7 a Cancelled request does not time out: green on activation as predicted; trial (guard call
  removed) failed exactly T6 and T7 — as predicted.

## Lifecycle — reconstitution with marks and sides
- Scaffold: `Help.reconstitute(..., accepted, rejected)` overload ignoring `rejected`.
- Red: only `reconstitutionKeepsMarksAndSides`, first Help `(HELP_ID, false, false)` instead of
  `(HELP_ID, false, true)` — as predicted. Green: pass `rejected` through; 59 passed, 0 skipped.
- Open for step 7: the persistence mapper still uses the 5-argument form; the stored Help has no
  `rejected` column yet.

## Properties
- INV-06 `newDeadlineIsAlwaysRejectionTimePlusTwelveHours`: green on activation as predicted.
  Trial: `askAgain` extends the old deadline (old + 12 h). Predicted: the property, T5, R1, R2
  fail. **Actual: the property, R1, R2 failed; T5 stayed green — prediction mismatch.**
  Cause: T5 only asserts that the new deadline lies after Sat 06:00:01 (one-sided); Sat 18:00 is
  after it too. The exact value is pinned by R1, R2 and the property. Decision (user): journal
  only, no new test.
- INV-09 `answerCountsExactlyUntilTheDeadline`: green on activation as predicted (jqwik edge
  cases include the deadline instant). Trial: the deadline instant counts as late in
  `ensureAnsweredInTime`. Predicted: the INV-09 property, `answerAtTheDeadlineCounts` and the
  INV-06 property (its answer offset reaches 12 h) fail with `AnswerTooLate`. **Actual: as
  predicted** (3 errors).

## Refactoring
- Groups S02/S03 and S04/S05: isolated refactor agent, tests unchanged (`diff -r`), kept.
- Group S06 (isolated agent, tests snapshotted, `diff -r` empty; user: keep): retained 4 —
  the suggestion rule named `providersNotYetAsked()` (INV-09; domain-boundary trial: guards →
  state → event in `timeOut`), the 5-argument `Help.reconstitute` delegates to the 6-argument
  one, `now` → `timedOutAt`, members moved into slice order (S01–S06). Undone 0. Left alone:
  the three lifecycle guards (one rule each), the two deadline guards (the boundary lives once in
  `deadlinePassedAt`). Agent slip: two trials sent in one batch, the incomplete one reverted
  before the check. Agent mismatch: Spotless — the only violation was a line of
  `HelpRequestPropertiesTest` written in the INV-09 activation, fixed afterwards with
  `spotless:apply` (layout only, user approved).
- Proposal → planned list, inc 4 (user): the INV-01 Chef check (`contains(CHEF)`) and
  `PREPARATION_STEP_PROVIDERS` could disagree when a fourth provider type comes — a spec
  question for the Chef board.

## Unit checkpoint
- `mvn -o clean verify`: green — cooking-assistance 202 + 56, larder-app 9 + 29 (12 skipped:
  the increment 1 acceptance scenarios, undefined steps until step 12); Spotless, Error Prone,
  Spectral green.
- Complexity (PMD not configured; counted by hand): `HelpRequest` 16 `if`, each in its own guard;
  command methods without branches; highest McCabe ≈ 3 (`ensureNoteGiven`).

## Summary
- Test items 33/33 active (32 approved + 1 added); behavioral prediction mismatches 1 (INV-06
  trial, T5); process slips 1 (activation 7); refactor groups 3, all kept.
- For step 7: replace the four outbox placeholders test-first; store the Help's `rejected` mark.
