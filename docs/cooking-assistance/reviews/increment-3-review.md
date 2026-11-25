# Independent review — Cooking Assistance increment 3 (Board B4)

Step 13, US-CA-04, slices B4-S01 and B4-S02, with B3-S06's time-out per side (B3-S03 to B3-S05 reused). Moderator: the orchestrating session. Reviewers were separate agents in fresh contexts, read-only; the inspectors did not open the ledger or the journals.

## 1. Reverse check (documents against code)
- Run at the end of step 12 (read-only agent): 12 findings, resolved there by the user (a new listHelps test H9, step 3 r24 for R4.E1, R5.E2 and E4 strengthened, the rest kept as open notes); see the step 12 journal. Entry criterion met: no open drift.
- Scoped, after the rework of this step (read-only agent, Sonnet, by reading): 15 claims; 3 drifts — ADR0021's Decision and Consequences and INV-09's r5 clause not marked as replaced → documents corrected (markers; INV-09 later rewritten, R-1). Open none.

## 2. Fagan inspection
- Package: the diff `origin/main..HEAD` after step 12 (69 files without ledger and journals), the approved artifacts (boards and slices, brief, invariants with the B4 Example Mapping r20–r24 and its readiness, OpenAPI 1.0.18, AsyncAPI 1.0.14, CHANGELOG, ADR0006–0021, SADR0008, quality scenarios, decision questions), step 12's coverage report and the planned list.
- Two inspectors, Sonnet, `fagan-code-inspector`, in parallel: domain (1 major, 4 minor, 5 questions), architecture (1 major, 4 minor, 5 questions). Merged to one major and six minors.
- Not inspected: generated code; parts of the larger test bodies (CatastropheSteps, HelpRequestCatastropheTest, the query and controller ITs) beyond their titles, traces and samples.

| # | Sev | From | Defect | Decision |
|---|---|---|---|---|
| M-1 | major | A-1, D-1 | The time-out works per provider side for every request type (claim when any asked side is due; a side times out alone without an event), while ADR0021's Decision and INV-09/INV-17 described a time-out of the whole request; the step 7 contract test `aRequestWithARunningSideIsNotDue` had been replaced (user decision of 2026-11-13) | document correction: ADR0021 addition (step 5) and step 3 r25 at INV-09 and INV-17; code unchanged |
| m-2 | minor | A-2, A-3, D-2 | Two items planned for increment 3 not done: an unknown stored `answerType` read silently as a preparation-step explanation; the test-only 12- and 15-argument `HelpRequest.reconstitute` | reworked: step 7 test-first (`AnswerJsonMapperTest`, Red/Green), overloads removed by a refactor agent (11 test call sites, kept) |
| m-3 | minor | A-4 | The new quality-test requests sent version 1.0.10, not the current 1.0.18 | reworked: `TimeOutTimelinessIT`'s new helpers send 1.0.18 |
| m-4 | minor | A-5 | The coverage report claimed QS-09 "met per side", though "once per deadline" is not observable for a side that times out alone | reworked: QS-09 met for the request's time-out; a side's time-out shows in its state |
| m-5 | minor | D-3 | `eventually` sleeps 100 ms in its loop | rejected: a bounded poll against the background poller is the method step 12 prescribes |
| m-6 | minor | D-4 | R1.E3's domain test cannot fail; R5.E2 checks "not TIMED_OUT" rather than one state | rejected: R1.E3 shows the absence of a plan guard (INV-18); R5.E2's check was chosen by the user in step 12 |
| m-7 | minor | D-5 | A catastrophe body with `howToStep` or `ingredient` might be accepted silently | rejected: the OpenAPI schema forbids those fields for this type |

Questions, answered by the author:
- Architecture Q-1 → M-1. Q-2: the per-operation `phases.coverage.enabled = false` is honoured by Schemathesis 4.1.4 (the rerun's Coverage phase covered 9 operations, no RecursionError). Q-3: QS-16's CI duration is open until the pull request. Q-4: the earlier-notes query has no recipe index; it reads by `(requester, id)` and is bounded per Cook with SEC-18 (increment 4). Q-5: the compatibility gate ran locally against `origin/main`, not yet in CI.
- Domain Q-1 → M-1. Q-2: a mitigation naming a recipe for a request without one is refused (INV-37: the request's recipe, if it names one). Q-3: a rejection after one side timed out asks both again (INV-23, "the same providers"); untested — open note. Q-4: an Answered request's asked side does not time out (R9.E5, INV-09). Q-5: no rule requires a non-blank explanation.

## 3. Rework
- M-1: ADR0021 addition (step 5 revision from step 13, increment 3) and "superseded in part" markers on its Decision and Consequences; step 3 r25 at INV-09 and INV-17; INV-09's clause rewritten (R-1).
- m-2: `AnswerJsonMapperTest.anUnknownStoredAnswerTypeIsRefused` Red ("Expecting code to raise a throwable"), Green (explicit case, refusing default); refactor agent removed the 12- and 15-argument `reconstitute` (fast suite 584, repository and query ITs 128, repository contract against PostgreSQL 17 tests, test names unchanged; kept by the user).
- m-3, m-4: as in the table.
- Fast and slow suites after the rework: green (584 + 216, 14 + 95; 319 s).

## 4. Follow-up
- Re-inspection by a fresh inspector (Sonnet, not one of the first two), changed units only: M-1, m-2 part 2, m-3, m-4 resolved; two new minors:
  - R-1: INV-09 kept the replaced rule in its running text, corrected only by a bracket → rewritten (with the user's consent); ADR0021 keeps its Decision with the marker (ADRs are amended by additions).
  - R-2: a stored answer with no `answerType` field ended in a NullPointerException → step 7 test-first: `aStoredAnswerWithoutAnswerTypeIsRefused` Red (NullPointerException), Green (`path("answerType")`); fast suite 585.
- Full `verify` on the final state: green (cooking-assistance 585 fast + 216 ITs, larder-app 14 fast + 95 ITs; 324 s).
- Exit: no accepted major defect open.

## 5. Counts
- Inspection: major 1 (accepted, document correction), minor 6 (accepted 3, rejected 3); questions 10, all answered.
- Follow-up: major 0, minor 2 (both reworked).
- Reverse check after the rework: 15 claims, 3 drifts corrected, open none.
