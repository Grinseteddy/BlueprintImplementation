# Journal — increment 0, step 8 (Application services)

## Readings confirmed by the user
- REST ProvideHelp retries once on ConcurrencyConflict (ADR0010); the Grandma handler relies on broker redelivery (ADR0015).
- Unknown request / domain rejection in the Grandma handler pass through unchanged; step 9 dead-letters with the rule ID.
- Role `community` answers via REST (ADR0013); IDs from an IdGenerator port (UUIDv7, ADR0011).
- Fagan code inspector is now installed → step 13 uses it (no fallback).

## Test lists
- Contracts: HelpRequestRepositoryContract (5), ProcessedMessagesContract (2) — run by InMemory*Test (fast) and Jdbc*ContractIT (slow).
- Use cases: RequestHelpWithPreparationStepTest (7), ProvideHelpTest (11), ProvideGrandmaAvatarHelpTest (5).
- Prediction: green; the new tests skipped. **Actual: as predicted.** (Note: counts were also stated; per the blueprint adaptation only the behavioral part counts as evidence.)

## Cycle 1 — repository contract test 1 (unknown id absent)
- Scaffold: test fake InMemoryHelpRequestRepository (findById → null, save no-op, recordedEventCount 0).
- Prediction (Red): fake run fails "Expecting actual not to be null"; JDBC run passes. **Actual:** fake run as predicted; the JDBC run did not execute in that build (fast suite failed first) → ran the slow suite alone: JDBC run passes, as predicted.
- Green: fake findById → Optional.empty(). Prediction: both runs pass.
  **Actual: as predicted.** Refactor: no change.

## Cycle 2 — repository contract test 2 (found equal, no pending events)
- Mechanical move: HelpRequests fixture → shared public org.larder.cookingassistance.fixtures (persistence ITs: import only).
- Prediction (Red): fake run fails "Expecting Optional to contain a value but it was empty". **Actual: as predicted.**
- Prediction: JDBC run + all step 7 ITs pass. **Actual: as predicted** (slow suite alone, green).
- Green: fake stores Snapshot records, findById reconstitutes. Prediction: fake runs 1–2 pass. **Actual: as predicted.** Refactor: no change (Help/AskedProvider immutable).

## Cycle 3 — repository contract test 3 (versions 1, 2)
- Prediction (Red): fake run "expected: 1L but was: 0L"; JDBC run passes. **Actual: as predicted** (both halves).
- Green: fake stores version + 1. Prediction: fake 1–3 pass.
  **Actual: as predicted.** Refactor: no change.

## Cycle 4 — repository contract test 4 (concurrent save conflict)
- Prediction (Red): fake run "Expecting code to raise a throwable."; JDBC run passes. **Actual: as predicted** (both halves).
- Green: compare-and-set on the stored version (absent = 0). Prediction: fake 1–4 pass.
  **Actual: as predicted.** Refactor: no change.

## Cycle 5 — repository contract test 5 (pending events recorded)
- Prediction (Red): fake run "expected: 1 but was: 0"; JDBC run passes (5/5). **Actual: as predicted** (both halves).
- Green: fake pulls and records pending events on save. Prediction: fake 5/5 pass. **Actual: as predicted.** Repository fake passes the same contract as PostgreSQL.
- Refactor (Rule 4): recordedEventCount removed; contract subclass uses recordedEvents().size(). Prediction green.
  **Actual: green.** Retained.

## Cycle 6 — processed-messages contract test 1
- Scaffold: test fake InMemoryProcessedMessages (returns false).
- Prediction (Red): fake run "Expecting value to be true but was false"; JDBC run passes. **Actual: as predicted** (both halves).
- Green: fake keeps a Set; returns Set.add(id). Prediction: fake test 1 passes.
  **Actual: as predicted.** Refactor: no change.

## Cycle 7 — processed-messages contract test 2
- Prediction: green on activation in both halves. **Actual: as predicted.**
- Trial: fake always returns true. Prediction: only fake test 2 fails. **Actual: as predicted.** Restored; both fakes pass their full contracts, same as PostgreSQL.

## Cycle 8 — RequestHelpWithPreparationStep test 1 (happy path)
- Scaffold: application Role, Actor(id, roles, premium), IdGenerator; use case with Command (domain types), execute → null.
- Prediction (Red): "expected HelpRequestId[23a8eeed…] but was null". **Actual: as predicted.**
- Green: create via domain factory with a new id from IdGenerator and actor id as requester, save, return id. Prediction: test 1 passes.
  **Actual: as predicted.** Refactor: no change (no business decision in the use case).

## Cycle 9 — RequestHelpWithPreparationStep test 2 (event saved)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: use case pulls events before save. Prediction: only test 2 fails. **Actual: as predicted.** Restored; green.

## Cycle 10 — RequestHelpWithPreparationStep test 3 (ChefNotAllowed passes through)
- Test helpers lenaAsks(actor, recipe, step, providers…), assertNothingSaved().
- Prediction: green on activation. **Actual: as predicted.**
- Trial: catch ChefNotAllowed → IllegalArgumentException. Prediction: only test 3 fails. **Actual: as predicted.** Restored; green.

## Cycle 11 — RequestHelpWithPreparationStep test 4 (NoHelpProviderChosen passes through)
- Prediction: green on activation; trial (catch → IllegalArgumentException) fails only test 4. **Actual: both as predicted.** Restored; green.

## Cycle 12 — RequestHelpWithPreparationStep test 5 (RecipeRequired passes through)
- Prediction: green on activation; trial fails only test 5. **Actual: both as predicted.** Restored; green.

## Cycle 13 — RequestHelpWithPreparationStep test 6 (PreparationStepRequired passes through)
- Prediction: green on activation; trial fails only test 6. **Actual: both as predicted.** Restored; green.

## Cycle 14 — RequestHelpWithPreparationStep test 7 (role cook required)
- Scaffold: application error NotAuthorized(command, requiredRole).
- Prediction (Red): "Expecting code to raise a throwable." **Actual: as predicted.**
- Green: access check first (role COOK, else NotAuthorized). Prediction: 7/7 pass. **Actual: as predicted.**
- Refactor: no change; the only `if` is the ADR0013 access check (authorization, not a business rule). Shared access helper when ProvideHelp adds the second check.
- Unit RequestHelpWithPreparationStep complete: 7/7.

## Cycle 15 — ProvideHelp test 1 (happy path)
- Scaffold: use case ProvideHelp with Command(actor, helpRequestId, answerTitle, answer), execute → null.
- Prediction (Red): "expected HelpId[a9caf90d…] but was null". **Actual: as predicted.**
- Green: load (orElseThrow for now) → provideHelp(new id, community(actor), …) → save → return id. Prediction: test 1 passes.
  **Actual: as predicted.** Refactor: no change (load → call → save → return; no business decision).

## Cycle 16 — ProvideHelp test 2 (HelpProvided saved)
- Prediction: green on activation; trial (pull events before save) fails only test 2. **Actual: both as predicted.** Restored; green.

## Cycle 17 — ProvideHelp test 3 (unknown request → HelpRequestNotFound)
- Scaffold: application error HelpRequestNotFound(id).
- Prediction (Red): wrong throwable — NoSuchElementException instead of HelpRequestNotFound. **Actual: as predicted** ("No value present").
- Green: orElseThrow(HelpRequestNotFound). Prediction: tests 1–3 pass.
  **Actual: as predicted.** Refactor: no change.

## Cycle 18 — ProvideHelp test 4 (OwnRequest passes through)
- Test helper assertNoHelpSaved(); trial script trial_lose.py (scratchpad).
- Prediction: green on activation; trial fails only test 4. **Actual: both as predicted.** Restored; green.

## Cycle 19 — ProvideHelp test 5 (AnswerDoesNotMatchRequest passes through)
- Prediction: green on activation; trial fails only test 5. **Actual: both as predicted.** Restored; green.

## Cycle 20 — ProvideHelp test 6 (TooManyPictures passes through)
- Prediction: green on activation; trial fails only test 6. **Actual: both as predicted.** Restored; green.

## Cycle 21 — ProvideHelp test 7 (ProviderNotAsked passes through)
- Own fake with a Grandma-only request.
- Prediction: green on activation; trial fails only test 7. **Actual: both as predicted.** Restored; green.

## Cycle 22 — ProvideHelp test 8 (InvalidHelpRequestState passes through)
- Own fake with a reconstituted CANCELLED request.
- Prediction: green on activation; trial fails only test 8. **Actual: both as predicted.** Restored; green.

## Cycle 23 — ProvideHelp test 9 (conflict retried once)
- Fake: test hook interleave(otherWriter), run once inside the next save before the version check (contract still green).
- Prediction (Red): ConcurrencyConflict thrown from execute (no retry). **Actual: as predicted.**
- Green: execute retries provideHelpOnce once on ConcurrencyConflict. Prediction: test 9 passes, earlier green.
  **Actual: as predicted.** Refactor: no change.

## Cycle 24 — ProvideHelp test 10 (second conflict propagates)
- Third writer Sam via nested interleave.
- Prediction: green on activation; trial (retry twice) fails only test 10. **Actual: both as predicted.** Restored; green.

## Cycle 25 — ProvideHelp test 11 (role community required, before loading)
- Observable "before loading": unknown request id + cook-only actor.
- Prediction (Red): wrong throwable — HelpRequestNotFound instead of NotAuthorized. **Actual: as predicted.**
- Green: role COMMUNITY checked first. Prediction: 11/11 pass.
  **Actual: as predicted.**
- Refactor (boundary trial): ADR0013 access rule duplicated in two use cases → Actor.require(role, command). Prediction green.
  **Actual: green.** Retained — the access rule lives once, on the actor; each use case names only its role. Unit ProvideHelp complete: 11/11.

## Cycle 26 — ProvideGrandmaAvatarHelp test 1 (her answer becomes a Help)
- Scaffold: handler with own-language GrandmaAnswer(messageId, helpRequestId, answerTitle, answer), empty handle; test fake RecordingUnitOfWork (runs work, isActive; commit only).
- Prediction (Red): "Expected size: 1 but was: 0". **Actual: as predicted.**
- Green: load (orElseThrow) → provideHelp(new id, grandmaAvatar(), …) → save. Prediction: test 1 passes.
  **Actual: as predicted.** Refactor: no change (shared shape with ProvideHelp, different surroundings; one handler only).

## Cycle 27 — ProvideGrandmaAvatarHelp test 2 (redelivery has no effect)
- Prediction (Red): "Expected size: 1 but was: 2". **Actual: as predicted.**
- Green: skip when recordFirstProcessing is false. Prediction: tests 1–2 pass.
  **Actual: as predicted.** Refactor: no change (the `if` is the ADR0015 dedup mechanism).

## Cycle 28 — ProvideGrandmaAvatarHelp test 3 (record and save in one unit of work)
- Spies note RecordingUnitOfWork.isActive() at record and save.
- Prediction (Red): fails because the record happens outside a unit of work (message "Expecting value to be true but was false"). **Actual:** fails for that reason; the message text differs — AssertJ's AtomicBoolean assertion prints "Expecting AtomicBoolean(false) to have value: true". Behavioral part as predicted; wording difference noted, not investigated further.
- Green: handle wraps dedup + load + provideHelp + save in unitOfWork.transactionally. Prediction: tests 1–3 pass.
  **Actual: as predicted.**
- Refactor (Rule 2): rename provideHelpOnce → provideHelpIfFirstDelivery (avoid clash with ProvideHelp's retry meaning). Prediction green.
  **Actual: green.** Retained.

## Cycle 29 — ProvideGrandmaAvatarHelp test 4 (unknown request → HelpRequestNotFound)
- Prediction (Red): NoSuchElementException instead of HelpRequestNotFound. **Actual: as predicted.**
- Green: orElseThrow(HelpRequestNotFound). Prediction: tests 1–4 pass.
  **Actual: as predicted.** Refactor: no change.

## Cycle 30 — ProvideGrandmaAvatarHelp test 5 (domain rejection propagates)
- Own fake with a reconstituted CANCELLED request.
- Prediction: green on activation; trial (silent swallow of DomainRejection) fails only test 5. **Actual: both as predicted.** Restored; green. All step 8 tests active.

## Finish — added on the way (user approved)
- UuidV7IdGeneratorTest (2, adapter.out.id): version 7 + IETF variant; later instants sort after earlier ones (ADR0011).
- ApplicationWiringIT (3, slow): one happy path per application unit through the real adapters.
- Prediction: green, new tests skipped. **Actual: as predicted.**

## Cycle 31 — UuidV7IdGenerator test 1 (version 7, IETF variant)
- Scaffold: adapter.out.id.UuidV7IdGenerator(Clock) returning a zero UUID.
- Prediction (Red): "expected: 7 but was: 0". **Actual: as predicted.**
- Green: RFC 9562 layout (48-bit ms, version 7, 12+62 random bits, variant 10). Prediction: test 1 passes.
  **Actual: as predicted.** Refactor: no change.

## Cycle 32 — UuidV7IdGenerator test 2 (time-ordered)
- Prediction: green on activation. **Actual: as predicted.**
- Trial 1 — random time bits, 3 quiet runs. Prediction: fails in some runs. **Actual — MISMATCH:** grep counted 0 failures. Explanation: the trial was non-deterministic by design (≈50 % chance per run), and the quiet-mode grep pattern may not match quiet output — not usable as evidence. Stopped for the human decision.
- Trial 2 (user chose deterministic): reversed time bits ((1<<47) − millis), one run with full output. Prediction: only test 2 fails. **Actual: as predicted.** Restored; 2/2 green. Lesson: trials must fail deterministically; no quiet-mode greps for evidence.

## Cycle 33 — ApplicationWiringIT test 1 (RequestHelpWithPreparationStep wired)
- Prediction (Red): UnsatisfiedDependencyException (no RequestHelpWithPreparationStep bean). **Actual:** as predicted for the active test — **MISMATCH** for the 2 disabled tests: reported as errors (same field-injection failure), not skipped. Explanation: Spring injects the test instance's fields even for disabled methods here. Stopped; user chose Green as the check.
- Green: adapter.config.ApplicationConfiguration — Clock.systemUTC, UuidV7IdGenerator, RequestHelpWithPreparationStep. Prediction: test 1 passes; the 2 disabled tests skipped again. **Actual: as predicted** — explanation of the mismatch confirmed.

## Cycle 34 — ApplicationWiringIT test 2 (ProvideHelp wired)
- Prediction (Red): all 3 tests error, no bean of type ProvideHelp. **Actual: as predicted.**
- Green: ProvideHelp bean. Prediction: 1–2 pass, 3 skipped.
  **Actual: as predicted.** Refactor: no change.

## Cycle 35 — ApplicationWiringIT test 3 (Grandma handler wired)
- Prediction (Red): all 3 error, no bean of type ProvideGrandmaAvatarHelp. **Actual: as predicted.**
- Green: ProvideGrandmaAvatarHelp bean (real repository, processed messages, unit of work). Prediction: 3/3 wiring pass, full verify green. **Actual: as predicted** (62 fast, 29 slow, 0 skipped).

## Finish checks
- Every if/catch in application: Actor.require (ADR0013 access), ProvideHelp catch (ADR0010 retry), ProvideGrandmaAvatarHelp if (ADR0015 dedup) — no business decision.
- Infrastructure imports in application: none.

## Slice refactoring — B3-S01, step 8 (EXACT refactor agent, fresh)
- Snapshot saved (scratchpad refactor-B3-S01-step8/). Report complete. Change: RFC 9562 field names for the UUIDv7 masks (RAND_A_12_BITS, RAND_B_62_BITS). Rejected: access-policy unit, use-case base class (no second use of the knowledge).
- Proposals: 48-bit timestamp mask (year 10889) and strict order within one millisecond — both ADR0011 decisions, logged as open questions, not needed for the skeleton.
- Verified: tests unchanged; only UuidV7IdGenerator differs; verify green (62/29). Decision (user): keep.

## Slice refactoring — B3-S02, step 8 (EXACT refactor agent, fresh)
- Snapshot saved (scratchpad refactor-B3-S02-step8/). Report complete. Production unchanged (trial: shared not-found lookup — undone on design grounds). Retained: fake InMemoryHelpRequestRepository internals (NO_OTHER_WRITER constant, letOtherWriterSaveFirst()); contract unchanged.
- Proposals: UnitOfWork void overload (transactionally(Runnable)) — port change, open question; retry-once policy only when a second use case needs it.
- Unit approved (user): keep; commits 48687df (feat, verified pre-agent state), 9dc7a86 (refactor, verified).
