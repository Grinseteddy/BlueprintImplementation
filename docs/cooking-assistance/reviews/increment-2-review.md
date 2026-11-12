# Independent review — Cooking Assistance increment 2 (Board B2)

Step 13, US-CA-02, slices B2-S01 and B2-S02 (B3-S03 to B3-S06 reused). Moderator: the orchestrating session. Reviewers were separate agents in fresh contexts, read-only; the inspectors did not open the ledger or the journals.

## 1. Reverse check (documents against code)
- Run at the end of step 12 (read-only agent, scoped to step 12): 11 drifts, all resolved there by the user (step 1 r1, step 3 r16, ADR0016, report wording, two step 12 fixes); see the step 12 journal. Entry criterion met: no open drift.

## 2. Fagan inspection
- Package: the diff 8bbc221..HEAD after PR #8 (107 files, without ledger and journals), the approved artifacts (boards and slices, brief, invariants and the B2 Example Mapping, OpenAPI 1.0.15, AsyncAPI 1.0.11, ADR0006–0021, SADR0008, quality scenarios), step 12's results and the planned list.
- Two inspectors, Sonnet, `fagan-code-inspector`, in parallel: domain (1 major, 3 minor, 2 questions), architecture (1 major, 5 minor, 3 questions). No duplicates.
- Not inspected: generated code; parts of the larger test bodies (`HelpRequestIngredientTest`, `HelpRequestQueryIT`, controller tests) beyond their titles and traces (architecture inspector).

| # | Sev | Defect | Decision |
|---|---|---|---|
| D-1 | major | US-CA-02 R5.E3 / INV-15 S4: the scenario shows the carried-over note, not that a proposed mascarpone is accepted on the later request | reworked: an S4 test existed at REST/DB level (`HelpRequestQueryIT`, traced S4 only); the scenario gains "Jonas's mascarpone for it is accepted" (trial in `ProvideHelp` failed only R5.E3), the IT traces R5.E3 |
| A-1 | major | QS-03/QS-11: a failed publish silently ends the Grandma Avatar's schedule; the run still passes ("list recent" on empty pages) | reworked: check "Grandma answered the last request" (Red with a failed first publish, all requests OPEN); `GrandmaAvatar` counts and logs failed publishes |
| D-2 | minor | R4.E2 "no good substitute" cannot be shown under INV-12 | document correction: step 3 r17; step text follows |
| D-3 | minor | INV-12 "with amounts": the aggregate accepted a missing, zero or negative amount and a missing unit; a missing recipe ended in an NPE | reworked: step 6, three domain tests Red/Green (`NoSubstituteProposed`, `AnswerDoesNotMatchRequest`) |
| D-4 | minor | R7.E4's final "no time-out message" check could not fail | reworked: removed |
| A-2 | minor | QS-08 measured from the 201, not the commit | reworked: measured from before the POST (an upper bound) |
| A-3 | minor | `provideHelp` answers 422 VALIDATION_FAILED, not in the spec's rule-violation list | deferred: with the NoHelpProviderChosen spec item (step 4) |
| A-4 | minor | QS-01's acceptance share and QS-16's value not reported | reworked: report states 49/49 and 5 min 47 s |
| A-5 | minor | `npx @asyncapi/cli` without lock or integrity; pyyaml without hashes | deferred: with SEC-11 (step 14) |
| A-6 | minor | The earlier-notes query reads all of one Cook's earlier requests (unbounded per Cook) | deferred: with SEC-18 (increment 4); ADR0016 wording corrected |

Questions, answered by the author:
- Domain Q-1 (R4.E2) → D-2. Domain Q-2 (amounts in the aggregate or at the edge) → D-3, in the aggregate.
- Architecture Q-1: the setup-node SHA `949feb24…` is the tag v7.1.0 (GitHub API). Q-2: `listHelpRequests` without `requester` reads across Cooks (SEC-18, increment 4), so the seed affects the list measurements. Q-3: the 317 ms before V9 is in the step 12 journal; the raw Gatling log was not kept.

## 3. Rework
- Branch `feature/cooking-assistance-inc2-review` from main a3e335e (PR #8 merged).
- Majors back to their owning steps, Red first: D-1 (step 12 scenario; the S4 behavior had its test in step 11), A-1 (step 12 harness).
- Minors batched: D-3 (step 6), D-4, A-2, A-4, D-2 step text, ADR0016 wording.
- Prediction mismatches: D-3's first Green failed in the new test's own arrange step (the creation event was not pulled; test fixed, user); the clean A-1 run started on an unemptied database (1 OPEN left over).
- After all rework: `verify` green (cooking-assistance 493 fast + 189 slow, larder-app 14 fast + 69 slow; JaCoCo, Spotless, Error Prone, Spectral); QS-08 p95 1.0 s; intent coverage no gaps.

## 4. Reverse check of the rework and re-inspection
- Re-inspection: a new inspector (Sonnet, fresh context, not one that found the defects), the changed units only. All seven reworked defects resolved; no new major.
- Reverse check of the rework: a separate read-only agent, 6 drifts, all minor.

| # | Finding | Decision |
|---|---|---|
| R-1 = RC-2 | Failed Grandma publishes were counted, never asserted: a lost answer mid-run passed unless it was the last | reworked: the check became "Grandma answered every request" (no request left OPEN after the load); trial with the 20th publish dropped failed the run; clean run passed |
| R-2 | The IT traced to R5.E3 shows only its second half | rejected: the scenario shows both halves; the IT proves S4 |
| RC-1 | Ledger named a "non-empty recent page" check that was not built | ledger corrected |
| RC-3 | A-3 wording too broad: over REST only a whitespace-only name reaches the 422; amount, unit and recipe get 400 from the schema | ledger corrected |
| RC-4 | INV-12 text said "a mix with amounts"; code and specs require an amount and unit on every ingredient | step 3 r18 |
| RC-5 | R5.E3 exercises Provide Help without `@B2-S02` | tag added |
| RC-6 | "…is accepted" could read as Accept Help | step renamed "…is not refused" |
| — | `NoSubstituteProposed`'s message did not mention amounts | reworded |

## 5. Result
- Exit criteria met: no accepted major defect open; every defect reworked, rejected with a reason, or deferred (A-3, A-5, A-6 on the planned list).
- Counts: inspection 2 major, 8 minor; re-inspection 0 major, 2 minor; reverse check of the rework 6 minor.
- Document revisions: step 3 r17 (R4.E2), r18 (INV-12); ADR0016 wording.
