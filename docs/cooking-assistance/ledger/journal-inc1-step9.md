# Journal — increment 1, step 9 (Inbound adapters), operations cancel, accept, reject and the time-out poller

Board B3, US-CA-03, slices B3-S03 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Generators unchanged (openapi-generator 7.26.0; no spec change, nothing new to generate — the
generated `HelpRequestsApi` and `HelpsApi` already declared the three operations, answered 501).
Fast loop: `CancelHelpRequestOperationTest`, `AcceptHelpOperationTest`, `RejectHelpOperationTest`
(`@WebMvcTest`, real security, step 8 use cases on the contract-checked fake), `TimeOutPollingTest`,
`CancelHelpRequestTest` (step 8 r1); slow: `InboundWiringIT` (Testcontainers PostgreSQL 16).

## Slice check (user 2026-11-04)
- cancelHelpRequest → CancelHelpRequest, acceptHelp → AcceptHelp, rejectHelp → RejectHelp, clock →
  TimeOutDueHelpRequests through a new poller in `adapter.in.scheduling` (ADR0021). Security
  unchanged: `POST /help-requests/**` already needs `help:write`. No new receive operation.
- F1 (defect in waiting): `HelpNotInRequest` (code NOT_FOUND) fell into the error map's 422
  default; acceptHelp declares 404 and no 422 → `statusOf` NOT_FOUND → 404 (A4).
- F2: the spec says 403 NOT_REQUESTER, the code answered NOT_AUTHORIZED. User: NOT_REQUESTER —
  `NotAuthorized` carries its code (step 8 r1, N1); the error map picks the `NamedProblem` entry.
- F3: `nextChef` on a preparation-step rejection. User: 501 until increment 4 (J5).
- F4: a note of spaces passes the spec (`minLength: 1`) and is refused by the domain (422 INV-06).
- F5: one run claims at most 100. User: drain in one poll while a batch comes back full (S2).
- The parameterized 501 test lost the cancellation, acceptance and rejection rows one by one in
  their Greens (title 7 → 6 → 5 → 4 unbuilt operations).

## Test list (approved 2026-11-04)
40 items: CancelHelpRequestOperationTest X1–X9, AcceptHelpOperationTest A1–A10,
RejectHelpOperationTest J1–J13, TimeOutPollingTest S1–S4, CancelHelpRequestTest N1 (step 8 r1),
InboundWiringIT W4–W6. No undeclared-field items (the spec sets no `additionalProperties: false`).

## Cancel (N1, X1–X9)
- N1: Red (`NOT_AUTHORIZED`) as predicted; Green a code field in `NotAuthorized`.
- X1: Red 501 as predicted; Green controller, mapper overload, test bean. X2: Red 500 (NPE, body
  null) as predicted; Green `Optional.ofNullable(body).map(Cancellation::getNote)`.
- X5: Red at `$.code` (NOT_AUTHORIZED) as predicted; Green `NamedProblem.NOT_REQUESTER` and
  `NamedProblem.valueOf(refusal.code())`.
- X3, X4, X6–X9 green on activation; each trial failed exactly that item.
- **Trial mismatch (X3):** the note's `maxLength` is guarded three times. Trial 1 (schema check off)
  stayed green as predicted (bean validation `@Size` behind it). Trial 2 (binder validation off too)
  failed with 500, predicted 200: the generated `@Validated` interface's method validation throws
  `ConstraintViolationException`, which the error map does not name. Not reachable today; user:
  journal and add to planned Q-02 (step 11).
- Process slips: X2's Green was applied and checked in one call without the prediction first
  (it would have been "all green"; it was); once a skip count was predicted (counts are not
  predicted; dropped).
- Refactor (isolated agent, tests unchanged): retained 0; boundary trial `NotAuthorized.Reason` enum
  with an exhaustive switch in the adapter — green, undone (`code()` must stay → the knowledge
  twice, a mapping decision in the adapter). User: keep.

## Accept and reject (A1–A10, J1–J13)
- A1, J1: Red 501 as predicted; Green `HelpsController` + `HelpMapper.toAcceptance` /
  `toRejection` + test beans.
- A4 (F1): Red 404 vs 422 as predicted; Green `statusOf` NOT_FOUND → 404.
- J5 (F3): Red 200 vs 501 as predicted; Green `nextChef != null` → `NotImplemented`.
- All other items green on activation; each trial failed exactly that item. Where a plain removal
  would not fail the test (a second guard behind it), the trial changed the answer instead: A2
  (`handleTypeMismatch` → NOT_FOUND; the unnamed-400 fallback is VALIDATION_FAILED too), J2/J3
  (schema violations → NOT_FOUND; bean validation behind the schema check), A8/J11 (handler on
  another type; without the retry the first conflict is 409 too). Domain guards were trialled at
  their source (A7/J10 `ensureNotRejected`, J4 `isBlank`, J9 `ensureAnswered` → 404 through the
  next guard, as predicted).
- Refactor (isolated agent, tests unchanged, one group as in step 8): retained 1 — the `nextChef`
  check moved from the controller into `HelpMapper.toRejection` (a translation limit next to the
  translation, like `toAnswer`'s default). User: keep.

## Poller (S1–S4) and wiring (W4–W6)
- S1: scaffold `TimeOutPolling` (empty `poll`); Red runs 0 vs 1; Green one `execute()`.
- S2 (F5): Red runs 1 vs 3; Green `do … while` a full batch. S3 green on activation; trial (an extra
  run) failed S1 and S3, S2 stayed green — as predicted.
- S4: Red (bean present when switched off); Green `@Configuration` + `@ConditionalOnProperty` only.
- Refactor (isolated agent, tests unchanged): retained 1 — `TimeOutDueHelpRequests.moreMayBeDueAfter`
  instead of a `batchSize()` accessor (the claim limit known in one class; the drain policy stays in
  the poller); the `TIME_OUT_BATCH_SIZE` comment corrected (per run, not per poll). User: keep.
- W4, W5 green on activation; trials (the controller skips the use case) failed exactly that item.
  The first W5 trial edit did not apply (text did not match the formatted code); that run was not
  counted, the trial was redone and matched.
- W6 (`@Nested` context, poller on, delay 100 ms, `Eventually` 5 s): Red OPEN as predicted; Green
  `@EnableScheduling` + `@Scheduled(fixedDelayString = "${cooking-assistance.timeout.poll-delay:10s}")`;
  the test properties switch the time-out poller off except where a test asks for it. Run twice.

## Unit checkpoint
- `mvn -o clean verify`: green — cooking-assistance 277 + 78, larder-app 9 + 29 (12 skipped: the
  increment 1 acceptance scenarios until step 12). InboundWiringIT ×2 green.
- Leaks: no generated, web, messaging or validation type in domain or application; no adapter
  import in the fakes.
- Every `if` in the changed adapters: `nextChef != null` (contract: unbuilt variant, F3),
  `case "NOT_FOUND"` (error map, F1), `while (moreMayBeDueAfter)` (drain policy, F5).
- Not checked by the runtime validator: response bodies are checked in tests (`SpecSchemas`), not
  at runtime.

## Summary
- Test items 40/40 active; behavioral prediction mismatches 0; trial mismatches 1 (X3, third guard);
  process slips 3 (X2 no prediction, a predicted count, a W5 trial edit that did not apply); trials
  29; refactor groups 3 (cancel no change, accept/reject 1 kept, poller 1 kept); sent back: step 8
  r1 (N1, NOT_REQUESTER).
- Planned: Q-02 gains the X3 finding (`ConstraintViolationException` → 500 when the earlier guards
  let a value through); `cooking-assistance.timeout.poll-delay` / `poller.enabled` as `LARDER_*`
  configuration in larder-app (README) with step 12.
