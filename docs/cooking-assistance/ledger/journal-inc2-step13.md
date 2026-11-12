# Journal — increment 2, step 13 (Independent review)

Board B2 "Help with ingredient substitution", US-CA-02. Fagan inspection per `fagan-code-inspector`; moderator the orchestrating session, author the same session (answers and rework only), inspectors separate read-only agents (Sonnet), decider the user. Branch `feature/cooking-assistance-inc2-review` from main a3e335e, after PR #8 (step 12) merged.

## Planning and overview
- Entry: step 12 green twice, intent coverage without gaps, step 12's scoped reverse check resolved.
- Package: diff 8bbc221..HEAD without ledger and journals; approved artifacts; step 12 results; the planned list. Overview written to the scratchpad (no design defence).
- Process slip: the first branch for the rework was created while PR #8 was still open (from the old main); deleted at once, the uncommitted ledger lines restored from the stash; the branch was made again after the merge.

## Inspection
- Domain inspector: D-1 major, D-2, D-3, D-4 minor, 2 questions. Architecture inspector: A-1 major, A-2 … A-6 minor, 3 questions. No duplicates.
- Author answers: setup-node SHA verified against the tag (GitHub API); the list without `requester` reads across Cooks (SEC-18); the 317 ms is kept only in the step 12 journal.
- Decisions (user): accept D-1, A-1, D-3, D-4, A-2, A-4; D-2 → step 3 r17; defer A-3, A-5, A-6 (ADR0016 wording corrected).

## Rework
- **D-1:** the S4 behaviour already had a REST/DB test (`HelpRequestQueryIT`, traced S4, not R5.E3), so the inspector's "no test" was half right. User: extend the R5.E3 scenario and the trace; no new `ProvideHelpTest`. The new step was green on activation; trial (`ProvideHelp` refuses a mascarpone answer on a request without Helps) failed only R5.E3, 422 vs 201, as predicted.
- **A-1:** check "Grandma answered the last request". Red: a forced failure of the first publish stopped the schedule, all 100 requests OPEN, run failed as predicted. Green: `GrandmaAvatar` counts and logs a failed publish and goes on; with the same forced failure the run passed (1 not published). Clean run passed; prediction slip: the database was not emptied, 1 OPEN left over from the Green run (user: go on).
- **D-3:** test list of three domain tests approved. Positive amount: Red as predicted (3 × no throwable); first Green failed in the new test's own arrange step — "no event" asserted without pulling the creation event (prediction mismatch, test fixed, user). Unit: Red and Green as predicted. Recipe: Red as predicted (NPE), Green by `requestedRecipe.equals(recipe)`.
- **D-4, A-2, A-4, D-2 step text, ADR0016:** as decided; `verify` green (493 + 189, 14 + 69), QS-08 p95 1.0 s, coverage without gaps.

## Re-inspection and reverse check of the rework
- New Sonnet inspector: all seven resolved, no new major; R-1 (failed publishes not asserted), R-2 (uncertain).
- Reverse check: RC-1 … RC-6.
- Decisions (user): R-1 → check "Grandma answered every request" (no request left OPEN); trial with the 20th publish dropped failed the run as predicted; clean run on an emptied database passed, 100 ANSWERED. R-2 rejected. RC-1, RC-3 ledger corrected; RC-4 step 3 r18; RC-5 tag; RC-6 step renamed; `NoSubstituteProposed` message names amounts.
- Final `verify` green (493 + 189, 14 + 69; JaCoCo, Spotless, Error Prone, Spectral); intent coverage without gaps; report `reviews/increment-2-review.md`.
