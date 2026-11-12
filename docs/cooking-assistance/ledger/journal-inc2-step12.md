# Journal — increment 2, step 12 (Tests)

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02 (B3-S03 to B3-S06 reused).
EXACT Coding at 88fb3f9, autonomy full-hitl. Infrastructure: Testcontainers PostgreSQL 16 and RabbitMQ 3.13 for the slow suite; for the load runs the same images as `docker run` containers, the service jar on 18080/18081 (8080/8081 taken on this laptop). Results on the laptop are indicative.

## Slice check and test list
- Spec gap: no ingredient or substitutes examples in either spec → step 4 r22 (OpenAPI 1.0.15 `ingredientSubstitute`, `substitutes`; AsyncAPI 1.0.11 `grandmaProposesSubstitutes`; additive; approved 2026-11-11).
- Coverage gate matched examples by bare id, so US-CA-03 R1.E1 covered US-CA-02 R1.E1 → examples match story **and** id (user).
- Spectral 6.11.1's example rule checks AsyncAPI 2 only → B3, a unit test validating every message example against its payload and header schemas (user).
- Quality scope: QS-08, QS-15, QS-01, QS-16, QS-03, QS-11. QS-12 and QS-13 have no slice → planned list, step 1 (user).
- Test list approved 2026-11-11: 0 r22; A feature US-CA-02 (7 rules, 24 scenarios); B1–B3; C quality; D1–D3 coverage.

## Cycles
- **A, acceptance:** 24 scenarios activated one at a time, each Red for its undefined or failing step, or green on activation with a trial in the service that failed the predicted step.
  - R3.E3: the amount was compared as text (`100.0` vs `100`) → numeric comparison (user approved; the run script also guards stale reports and Spotless).
  - R4.E2: the first trial produced a state the `HelpRejected` enum forbids (500, not the predicted step) → redone as a REST-only trial, failed as predicted (user).
  - Deadlock in the acceptance reset (truncate vs the relay) → retried truncate, moved to `DatabaseReset`, shared by `HelpRequestSteps`, `OpenApiExamplesIT`, `AsyncApiContractIT` (user).
- **B1, OpenApiExamplesIT D6:** the r22 examples did not fit together (the substitutes answer came from a provider the request did not ask) → the request asks both providers (step 4 r22, user). D6's trial also hit D5 through the shared database → `@BeforeEach` reset (user).
- **B2, AsyncApiContractIT D2b:** substitutes example accepted, the broken one dead-lettered; trial failed as predicted.
- **B3, `everyMessageExampleMatchesItsSchemas`:** green on activation; trial (a broken `grandmaProposesSubstitutes` answer) failed with its schema violation.
- **D1:** story-and-example matching; trial with the collision inventory failed as predicted.
- **QS-08 (E7, `OutboxTimelinessIT`):** first latencies were inflated by receiving only after sending → a concurrent receiver (user). p95 0.89 s ≤ 5 s; the trial failed (no event arrived within the deadline).
- **QS-15 (`spec-compatibility.sh`, `compatibility.yml`):** oasdiff 1.34.1 for OpenAPI; @asyncapi/cli 6.2.2 marked the `/info/version` edit breaking → override file (user); the CLI cannot judge payload changes → `asyncapi_as_openapi.py` + oasdiff (user). Seen failing on both specs (an enum value removed). First LITER trial edits were void (YAML list pattern) → redone.
- **QS-01 (JaCoCo 0.8.13, `domain-line-coverage`, 0.90):** coverage 100 % made the first 1.00 trial pass; stale appended data → `append=false` (user); without the domain tests 97.9 % → trial with 0.99 failed as predicted. `-Dtest='!…'` also ran ITs and exposed `EdgeTest`'s order dependence → `@BeforeEach` clear (sent back: step 9, user). Fast suite 33 s.
- **QS-03 / QS-11 (Gatling 3.16.0, plugin 4.21.12, module `load-tests`, profile `load`):** 20 HTTP requests/s (10 commands, 10 queries) plus 10 Grandma answers/s for 120 s.
  - The send channels had no bound queue, so the outbox never drained → queues bound in the load environment (as consumers do in production).
  - Seed: `make_interval(mins => numeric)` failed → `g * interval '630 seconds'`; the unseeded run was not counted.
  - QS-11 first passed because the list read the oldest page → the recent page (`status=ANSWERED`) added (user) → **missed**, p95 317 ms > 200 ms on 152,600 requests: the earlier-notes query walked the primary key over every earlier id and filtered by requester only then.
  - Sent back to step 7 (user): `v9IndexesRequestsByRequester` Red as predicted (after a run without a prediction that errored on the load broker's port 5672 — environment stopped first); V9 `help_request (requester, id)`, Green as predicted (31/31).
  - Rerun: QS-11 met (p95 create 22, read 24, list 45, recent 46 ms); QS-03 on an empty database met (18, 23, 43, 32 ms).
  - `load.yml`: on demand, weekly, on pull requests touching the load test; QS-03 empty, then seed, then QS-11; 20 min cap. Parsed; actionlint not installed, the first CI run is its check.
- **D2:** `inventory-inc2.yaml` (cumulative, US-CA-03 + US-CA-02, INV-01…17, 36–39; QS-08 added), `verify.yml` switched.

- **D2 trial:** an untested rule INV-99 in a copy of the inventory → exit 1, GAPS "rule INV-99", as predicted. **D3:** `intent-coverage-inc2.md` from the first full verify (US-CA-03 and US-CA-02 done, no gaps; INV-03 without a domain-level test, known).
- **Suites twice:** `verify` green both times (cooking-assistance 488 fast + 189 slow, larder-app 14 fast + 69 slow; JaCoCo, Spotless, Error Prone, Spectral), as predicted.

## Reverse check (read-only agent, scoped to step 12) and its resolution (user, 2026-11-12)
- RC-1 US-CA-02 R5.E3 "no mascarpone" contradicted S4 → step 3 r16. RC-2 R1.E4 "not offered" vs a refused request → step 3 r16 "asks chef Marco → refused" (as US-CA-03 r2); scenario title "Asking chef Marco is refused". RC-3 no user-story texts in `boards-slices.md` → step 1 r1.
- RC-4 ADR0016 named a planned (requester, recipe, how_to_step) index → V9 replaces it. RC-5 stale pause line → resumed line.
- RC-6/RC-7 QS-01 fast-suite time and QS-16 are observed, not gated (inventory header). RC-8 major-version route (QS-15), RC-9 QS-03's commands and Grandma processing time → planned list.
- RC-10 the seed's CLOSED requests had no Help → accepted Helps (50,000) in the seed; QS-11 rerun met (13/15/35/43 ms). RC-11 R5.E2 tagged `@B2-S02`.
- **Prediction mismatch:** the acceptance run after RC-11 failed in cooking-assistance's JaCoCo gate, not in AcceptanceIT: `-am -Dtest=NONE` ran no unit tests and `append=false` wrote empty data. Fix (user): profile `filtered-tests`, active with `-Dtest`, skips only `domain-line-coverage`. Rerun: AcceptanceIT 50/50, check skipped, no gaps. Trial: a full fast run with minimum 1.01 → BUILD FAILURE at the gate, as predicted; restored.

## Refactoring (isolated agents)
- **Group 1, tools** (`src/test` byte-identical by sha256 of 99 files; all kept by the user 2026-11-12):
  - one trace predicate `traces(kind, tokens, traced)` in `intent_coverage.py`, both reports byte-identical;
  - one `listRequests` request in the simulation;
  - `GrandmaAvatar` as its own class (domain-boundary trial: the answer's shape changes with the AsyncAPI receive channel, the simulation with the load profile);
  - `gatling:test` in `load.yml` (the plugin version only in `load-tests/pom.xml`);
  - spec-path variables in `spec-compatibility.sh`, dry run with stubs identical.
  - Smoke run (10 s, `gatling:test`): 0 failures, 97 of 100 requests answered by `GrandmaAvatar`.
  - Not applied, on the planned list (user): the version header from the spec, the send-channel queues from AsyncAPI, the seed's ids computed once.

- **Group 2, test support** (user's advance approval; kept 2026-11-12): the constant `LENAS_REQUIREMENTS` for the story's default requirement, five literals. Domain-boundary outcome: no split, since every request and answer body follows the same API contract. Features byte-identical; annotations, names and assertions diffed unchanged; full `verify` green. Proposals 1–3 on the planned list, proposal 4 (a stream lookup) dropped as cosmetic (user).

## Finish
- `verify` green three times (twice by me, once by the group 2 agent): cooking-assistance 488 fast + 189 slow, larder-app 14 fast + 69 slow; JaCoCo, Spotless, Error Prone, Spectral. AcceptanceIT 50/50 (US-CA-03 26, US-CA-02 24).
- Intent coverage `intent-coverage-inc2.md`: US-CA-03 and US-CA-02 done, no gaps; INV-03 without a domain-level test (known).
- Not yet run in CI: `load.yml`, `compatibility.yml`, the Schemathesis job with OpenAPI 1.0.15; QS-16 is read from the verify job's duration there.

## Corrections and slips
- The QS-01 gate as first built failed every filtered build; not foreseen when it was added (mismatch above).
- Prediction slips: a value (predicted OPEN, was ANSWERED); module counts misread.
- Faulty trials: one fired in the arrange step; three void edits; one undone by Spotless.
- One check run without a prediction (the first V9 Red).
- A placeholder assertion slipped into a step definition and was removed before its Red.
- A lambda variable shadowed a parameter (compile error) → renamed.
- A build output line addressed to AI agents (jqwik) is ignored as untrusted.
