# Journal — increment 1, step 8 (Application services), unit Help Request

Board B3, US-CA-03, slices B3-S03 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Fast loop: `CancelHelpRequestTest`, `AcceptHelpTest`, `RejectHelpTest`,
`TimeOutDueHelpRequestsTest`, the shared contract against the fake; slow:
`JdbcHelpRequestRepositoryContractIT`, `HelpRequestRepositoryIT`, `ApplicationWiringIT`
(Testcontainers PostgreSQL 16).

## Slice check
- Commands → use cases: Cancel help request → `CancelHelpRequest` (B3-S03), Accept help →
  `AcceptHelp` (B3-S04), Reject help → `RejectHelp` (B3-S05), Time out help request →
  `TimeOutDueHelpRequests` (B3-S06, policy Timeout).
- Access (ADR0013): cancel, accept and reject are for the requester only — a resource check in
  the application layer (`NotAuthorized` → 403 in step 9). One retry on `ConcurrencyConflict`
  (ADR0010). Domain rejections pass through unchanged.
- Step 5 later questions due now, answered by the user 2026-11-03: L4 → ADR0021 (a poller per
  instance every 10 s claims Open requests whose every deadline has passed, `FOR UPDATE SKIP
  LOCKED`, in one unit of work; the domain stays authoritative, INV-09); L3 → references kept as
  given (Recipe Catalog increment); L7 → own later increment before the first production release.
- The poller needs a finder the port did not have → `findDueForTimeOut(now, limit)` sent back to
  step 7 test-first (contract K1–K5, IT P10, step 7 r1 from step 8).

## Test list (approved 2026-11-03, with ADR0021)
37 items: CancelHelpRequestTest C1–C7, AcceptHelpTest B1–B8, RejectHelpTest R1–R7,
TimeOutDueHelpRequestsTest D1–D6, contract K1–K5 (fake and PostgreSQL),
HelpRequestRepositoryIT P10, ApplicationWiringIT W1–W3.
Titles reworded on the way (wording only, user saw them): B8 and C7 "Grandma answers once more in
between"; C6 names that the request keeps Grandma's Help after the retry.

## Use cases (C, B, R)
- Per use case: happy path, not found, requester only and retry Red as predicted, then Green.
  Event, pass-through and second-conflict items green on activation, each with a trial that
  failed exactly that item (Cancel 3, Accept 4, Reject 3).
- Process slip at C2: the C1 Green and the activation of C2 ran in one check, without C2's
  prediction first. Made up for with C2's own trial, which matched.
- Refactor after Accept and Reject (isolated agent, tests unchanged, user: keep both):
  `RetryOnConflict.once(...)` (ADR0010, now also used by `ProvideHelp`) and
  `Actor.requireRequesterOf(request, command)` with `NotAuthorized.requesterOnly(...)`.

## Finder and time-out (K, P10, D)
- K1, K2, K3, K5: Red then Green on the fake and on PostgreSQL. K4 (one side still running)
  green on activation; trial "any side" instead of "every side" failed exactly K4 in both runs.
- Addition while greening the claim query: `order by h.id` (deterministic batches) and an
  `OffsetDateTime` parameter instead of `Timestamp`.
- Compile mismatch: the anonymous spy repository in `ProvideGrandmaAvatarHelpTest` lacked the new
  port method (not predicted). User: delegate it in the spy.
- D1, D4, D5: Red then Green. D2, D3, D6 green on activation, each with a trial.
- D3 trial mismatch: D3 failed, but with `DeadlineNotPassed` from the domain guard instead of at
  the predicted status check — the aggregate guards it first. User: journal and go on.
- P10 (a claimed request is skipped by a second transaction and returned once the first commits):
  Red then Green; passed twice in a row (concurrency).
- Refactor (isolated agent, tests unchanged, user: keep): `claimDueIds(now, limit)` in the JDBC
  repository, `timeOutOneBatch()` in the use case.

## Wiring (W1–W3)
Green on activation once the beans were registered (`ApplicationConfiguration`,
`TIME_OUT_BATCH_SIZE = 100`); each trial (a use case without `save`) failed exactly that test.

## Unit checkpoint
- `mvn -o clean verify`: green — cooking-assistance 243 + 75, larder-app 9 + 29 (12 skipped: the
  increment 1 acceptance scenarios until step 12).
- Leak checks clean: no framework in domain or application; no adapter import in domain,
  application or fakes.

## Summary
- Test items 37/37 active; behavioral prediction mismatches 0; compile mismatch 1 (spy); trial
  mismatches 1 (D3); process slips 1 (C2); trials 17; refactor groups 2 (use cases kept,
  time-out kept); sent back: step 7 r1 (finder, K1–K5, P10).
- Planned: the fake returns due requests in HashMap order, PostgreSQL in id order — a contract
  item "due requests in id order" when something depends on it; the `@Scheduled` poller is step 9.
