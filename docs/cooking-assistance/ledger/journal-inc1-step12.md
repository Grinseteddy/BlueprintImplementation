# Journal — increment 1, step 12 (Service tests), the running service on real infrastructure

Board B3, US-CA-03, slices B3-S01 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Infrastructure: Testcontainers PostgreSQL 16 and RabbitMQ 3.13 (`rabbitmq:3.13-management-alpine`)
for every run; no fake broker, no imitation database. The service runs in-process on a random port
(`@SpringBootTest`), and locally as the jar for the Schemathesis runs. Started 2026-11-05, paused,
resumed and finished 2026-11-06.

## Slice check (user 2026-11-05)
- 12 `@planned` scenarios of US-CA-03 for this increment; every one already traced by lower-level
  tests. The real publisher is generic (A1–A6 since increment 0); the four new channels are proven
  end to end by the scenarios that read them (accepted, rejected, cancelled, timed out).
- Interpretations (accepted): R7.E1 "providers see the note" = `HelpRequestCancelled` carries the
  note; R5.E1 = `HelpRejected` with note and the new deadline; R8.E3 "Lena is told" =
  `HelpRequestTimedOut` with `suggestedProviders: []`.
- Decisions: a settable test clock; Schemathesis set up now; quality scenarios QS-09, QS-10, QS-14,
  QS-04/05 (QS-07 already shown by the read-your-writes tests).

## Returns to earlier steps
- **Step 3 r7 (user):** R5.E2 revised — "Both asked, Jonas answered, nothing accepted; Lena rejects
  Jonas's answer → sent again to both" (there is no "accept neither" command). R7.E1/E2 revised from
  "closed" to "cancelled" (the state is CANCELLED; R4 uses "closed" for CLOSED). invariants.md and
  the feature one to one. **Drift:** the source graph in `Requirements/` keeps the old wording of
  both — for the user to update upstream.
- **Step 4 r12, OpenAPI 1.0.9 (user):** request examples `withNote` (cancellation) and `warmWater`
  (rejection); from the Schemathesis triage `Problem.instance` is a `uri-reference` (RFC 9457) and
  `createHelpRequest`, `provideHelp`, `rejectHelp` declare 501; from QS-05 every operation declares
  503 `ServiceNotAvailableResponse`, its 500 now points to `UnexpectedErrorResponse` (it was
  mislabelled), `SERVICE_UNAVAILABLE` named. CHANGELOG under 1.0.9.
- **Step 5:** ADR0020 addition (user) — outbox backlog gauges on `/actuator/prometheus`, protected
  like the probes; alert rules an operations choice.
- **Step 6 r3:** the domain's `HelpType` names all four kinds of the spec (driven by L9).
- **Step 9 r1:** a body containing U+0000 answers 400 VALIDATION_FAILED (EdgeTest); a missing
  database connection, a transaction that cannot get one and a rollback failed after a lost
  connection answer 503 SERVICE_UNAVAILABLE (ProblemDetailsAdviceTest, four items).
- **Step 11 r1:** L9, H8 — a type filter the domain did not know answered 500; now 200 with no items.

## Test list (approved 2026-11-05)
H1–H2 (host settings), B1–B12 (`@planned` → `@wip`), C1 (OpenApiExamplesIT D2–D5), C2
(Schemathesis CI job), E1–E6 (quality scenarios), F1 (inventory for increment 1). Added on the way:
H3 (socket timeout), CompositionRootIT B2, B3, and the triage items above.

## Host settings (H1–H3)
- H1 `LARDER_TIMEOUT_POLLER_ENABLED`, H2 `LARDER_TIMEOUT_POLL_DELAY` (default 10 s, also the
  module's fallback — both kept, ADR0021), H3 `LARDER_DB_SOCKET_TIMEOUT` (default 10 s, pgjdbc
  `socketTimeout`). Each Red (null) / Green; README rows. Process slip: H3 was run without a
  prediction stated first.

## Acceptance (B1–B12), all 25 US-CA-03 scenarios active
- `AcceptanceClock` (ticking, moved by steps; `@Primary` only in the Cucumber context; story time
  Friday 6 November 2026, 18:00 CET per scenario); `LARDER_TIMEOUT_POLL_DELAY=200ms` in larder-app's
  test properties; the driver binds all six send channels (publishing is mandatory).
- Every scenario green on activation, each with a trial that failed exactly it (accept, provider
  type, 422 for INVALID_STATE, no re-ask, Community-only re-ask, later requests in previousNotes,
  dropped note, placeholder note, poller off, always-Grandma, suggest-anyway, a 2-minute margin).
- **Behavioral prediction mismatches:** B4 — an exact-second deadline against the ticking clock
  (+13 ms); user: 5 s tolerance. B12's trial — 409 not 422 (`AnswerTooLate` is INVALID_STATE), and a
  reset race: the clock left in the future let the time-out poller re-save the previous scenario's
  requests during the next reset; user: reset by one `TRUNCATE`, clock back after each scenario.
- One infrastructure failure (PostgreSQL SSL connection at context start), rerun green, not counted.
- Faulty trials (no result): one missing import, two edits that broke Spotless, a stale failsafe
  summary in a filtered build, one build without `-am` after `clean`.

## OpenAPI contract (C1, C2)
- C1: D2–D5 green on activation; trials (202 instead of 200; a Help without `answerTitle`) failed
  exactly the item each — the schema check is proven.
- C2: Schemathesis 4.1.4, seed 20261106, 25 examples, 20 requests/s, all 10 operations (every
  operation of 1.0.9 is built); test identity `tools/contract/test_identity.py` (user: a throw-away
  key and token, never used by a deployed service); `tools/contract/schemathesis.toml`;
  `.github/workflows/contract.yml` (pull requests and on demand, 20 min) — not yet run on GitHub.
  - Run 1: 17 failures, 6 causes → the spec corrections, the defects (L9/H8, NUL, Tomcat's HTML
    400 → `ProblemErrorReports` valve, CompositionRootIT B2) and one tool limitation (`if`/`then`).
  - Run 2: 6 → 501 accepted on the three operations; U+0000 in an ignored field name is refused
    (user: keep, exclude); undeclared query parameters are ignored (user: tolerant, exclude).
  - Run 3: 2 → `provideHelp` same policy; a missing required header: Schemathesis expects 406, the
    spec 400 (config aligned).
  - Run 4: 1 → TRACE answered 401 (the error dispatch to `/error` behind authentication; user: fix
    now) → CompositionRootIT B3 Red/Green, the host excludes `ErrorMvcAutoConfiguration`, the valve
    keeps 405. Tomcat's `Allow` lists its own methods (finding, not asserted).
  - Run 5: 1247 generated, 1247 passed. Warnings left: missing test data (generated ids are 404),
    the `if`/`then` mismatch on `createHelpRequest`.
  - Every exclusion is narrow and commented with the rule and the test that covers it instead.

## AsyncAPI
The receive operation is unchanged (AsyncApiContractIT); the four new send operations are covered by
B1, B4, B7, B9, whose driver validates every message against the spec.

## Quality scenarios (E1–E6)
- **E1 QS-09** (production poll delay 10 s): **mismatch** — only the timed-out channel was bound, the
  unroutable `HelpRequestCreated` held back the request's later rows (the planned risk "mandatory
  publishing blocks an aggregate with no bound queue", now shown by a test); user: bind all
  channels. Green; trial 45 s failed it.
- **E2 QS-09, two instances** (a second Spring context, indicative): green; trial (relay publishes
  twice) failed E1 and E2 — it shows the exactly-once check fails, not which mechanism (SKIP LOCKED
  or the version check) prevents a double time-out.
- **E3 QS-10:** green; no `ConcurrencyConflict` in the run (no collision this time, indicative);
  trial (lost update) failed it.
- **E4 QS-14:** Red 401 / Green `OutboxBacklogMetrics` (module), `micrometer-registry-prometheus`
  1.17.2 (fetched once, Boot's version), exposure `health,prometheus`, `ProbeSecurity`; trial
  (counting published rows) failed it.
- **E5 QS-04** (`docker pause` of the broker, 60 s; `@Tag("fault-injection")`, quality job):
  **mismatch** — `HelpRequestCreated` came twice with the same message id (at-least-once, ADR0015)
  and broke my exactly-once order check; user: deduplicate by message id. Green: one duplicate with
  the same id, both messages 34 ms after the unpause. **Limitation:** pause keeps the TCP
  connections, so reconnection and the L5 backoff are not exercised — open for QS-04.
- **E6 QS-05** (`docker pause` of PostgreSQL, quality job): Red 500 after Hikari's 30 s; user: spec
  503, error map 503, keep the 30 s. **Mismatch:** a request on a connection used within Hikari's
  500 ms bypass hung without limit (no socket timeout); user: H3. **Mismatch:** 500 again from
  `CannotCreateTransactionException` and a `TransactionSystemException` after a failed rollback;
  user approved three advice items. Green (52 s). Later found by the test-support refactor: E6
  counted all rows and failed together with E5 (shared context); user: count relative to before the
  outage. E5 and E6 green together.
- `quality.yml` runs the fault-injection group on demand and weekly (20 min); `verify` excludes it.

## Refactors (isolated agents, one after another, tests unchanged; all kept by the user)
- Host settings comment (ADR0021, ADR0019); acceptance steps and driver (domain-named helpers,
  imports, rule sections); module (the NUL constant and walk, handler grouping, `OutboxRelay.PENDING`
  shared by relay and gauges — a small step toward D-04); host valve (the problem type derived from
  the code once); test support (`HostSettings`, `ServiceChannels`, `HelpRequestBodies`, a raw-socket
  helper) — the last under the user's advance approval, after checking names, feature and tags.

## Intent coverage (F1)
`docs/cooking-assistance/coverage/inventory-inc1.yaml`: 24 examples, 13 rules, 10 HTTP operations,
6 send and 1 receive operations, 6 domain events, QS-02, QS-07, QS-09, QS-10, QS-14 (QS-04/05 by the
quality job); `verify.yml` uses it. Result and gate trial: see the ledger row of step 12.

## Open risks and planned list
- A send channel with no bound queue stops every later message of the affected requests (shown by
  E1) — an ADR0015 decision (give up `mandatory`, or dead-letter unroutable messages) for step 13 or
  operations.
- QS-04 with a real broker stop (reconnection, the L5 backoff) is not shown.
- A database outage still holds a request up to Hikari's 30 s (user: keep for now); pool timeouts as
  `LARDER_*` later.
- Container problems (P1 shared titles and base URI, P2 titles differ from the error map, P3 a
  container 503 → INTERNAL_ERROR) → step 13; the valve's JSON is not escaped, all values constants.
- Outbox poller `LARDER_*` variables; the poller's `DeadlineNotPassed` ERROR after the test clock
  moved back (test artifact); Tomcat's `Allow` on container 405s.
