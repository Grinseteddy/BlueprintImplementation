# Journal — increment 0, step 7 (Persistence)

## Setup and test lists
- User decisions: answer stored as jsonb (addition to ADR0009); units UnitOfWork, HelpRequestRepository, ProcessedMessages.
- POM: spring-boot-starter-data-jdbc, -json, flyway-core + flyway-database-postgresql, postgresql; tests spring-boot-starter-test, spring-boot-testcontainers, testcontainers postgresql + junit-jupiter; failsafe for *IT (slow suite in `verify`).
- Test support: PersistenceTestApplication, PostgresTestContainer (@ServiceConnection, postgres:16-alpine), test application.properties (schema cooking_assistance, locations db/cooking-assistance/migration).
- Inactive lists: UnitOfWorkIT 3, HelpRequestRepositoryIT 13, ProcessedMessagesIT 3.
- Prediction: BUILD SUCCESS; surefire 29 passed; failsafe 19 skipped; **no container started, no Spring context loaded** (instances only for running tests).
- **Actual — MISMATCH:** surefire 29 passed; failsafe 19 skipped, 0 failures/errors; but the PostgreSQL container started and Flyway ran (context loaded once, ~8.7 s in the first IT class). Stopped for the human decision (full-hitl).
- Investigation (user chose): prediction — context built and cached once in the first IT class at class level, other classes hit the cache. Run with org.springframework.test.context.cache=DEBUG: **actual as predicted** — missCount 1 in ProcessedMessagesIT (before any method), then only hits, cache size 1 across all three classes.
- Explanation: SpringExtension loads the context in its class-level callback, which JUnit runs even when all methods are disabled. Expectation error, not a defect; behavior matches ADR0017 (one container per run, reused). Mismatch closed.

## Order change
- Unit-of-work tests need a table from V1 → start with HelpRequestRepositoryIT test 1 (migration), then UnitOfWorkIT, rest of repository, ProcessedMessagesIT. Scope unchanged. V1 grows during step 7, committed at the end (applied only to throw-away test databases).

## Cycle 1 — repository test 1 (migration, ADR0009)
- Prediction (Red): table list holds only flyway_schema_history, none of the 5 tables; 18 ITs skipped; fast suite green. **Actual: as predicted.**
- Green: V1__create_help_request.sql with the five tables, keys and in-aggregate foreign keys only. Prediction: test passes, 12 skipped. **Actual: as predicted.**
- Refactor: no change; no cross-aggregate/cross-context FK.

## Cycle 2 — UnitOfWorkIT test 1 (ADR0015)
- Scaffold: port application.UnitOfWork (transactionally(Supplier)); adapter TransactionalUnitOfWork returning null without running the work.
- Prediction (Red): expected 1 but was 0. **Actual — MISMATCH:** error in @BeforeEach `delete from processed_message` → PSQLException "relation processed_message does not exist". Test setup/environment category, not behavior. Likely cause: Flyway creates the tables in schema cooking_assistance, but the JDBC connection's search_path is the default (public); the migration test only worked because it filtered information_schema by schema name. Stopped for the human decision.
- Investigation (user chose): prediction — default search_path "$user", public. Check on postgres:16-alpine (fresh initdb): **actual "$user", public** (first attempt printed only the auth warning; re-run on initialized cluster). Cause confirmed.
- Fix (setup, not behavior): spring.datasource.hikari.schema=cooking_assistance in test properties (real config in step 12). Mismatch closed.
- Prediction (Red, original): expected 1 but was 0. **Actual: as predicted.**
- Green: `return work.get();` (autocommit; the transaction comes with test 2). Prediction: passes, 2 skipped. **Actual: as predicted.**
- Refactor: no change; boundary candidate none (adapter delegates only).

## Cycle 3 — UnitOfWorkIT test 2 (rollback)
- Prediction (Red): exception reaches the caller, then count "expected 0 but was 1" (auto-committed); test 1 green; 1 skipped. **Actual: as predicted.**
- Green: TransactionTemplate.execute around the work. Prediction: UoW 1–2 pass, 1 skipped. **Actual: as predicted.**
- Refactor: no change. Order: UoW test 3 needs the repository → after the repository tests.

## Cycle 4 — repository test 2 (unknown id absent)
- Scaffold: port domain.helprequest.HelpRequestRepository (findById, save), ConcurrencyConflict next to the port, adapter JdbcHelpRequestRepository (findById → null, save no-op). @BeforeEach empties outbox, help, asked_provider, help_request.
- Prediction (Red): "Expecting actual not to be null"; migration test green; 11 skipped. **Actual: as predicted.**
- Green: findById returns Optional.empty() (lookup comes with the round-trip test). Prediction: 2 pass, 11 skipped.
  **Actual: as predicted.** Refactor: no change; boundary candidate none.

## Cycle 5 — repository test 3 (new OPEN request round-trips)
- Test fixture HelpRequests (domain commands only). No production scaffold.
- Prediction (Red): "Expecting Optional to contain a value but was empty"; tests 1–2 green; 10 skipped. **Actual: as predicted.**
- Green: V1 columns (help_request, asked_provider with position), HelpRequestRow + AskedProviderRow (Spring Data JDBC, ordered list), HelpRequestRows (CrudRepository), HelpRequestMapper (toDomain via reconstitute), adapter delegates. Prediction: passes; 10 skipped. **Actual: as predicted.**
- Refactor: asked-provider mapping extracted into toRow/toDomain. Adapter/mapper already separate (seam exists). Version always null in toRow — left for tests 8/9. Prediction green.
  **Actual: green.** Retained.

## Cycle 6 — repository test 4 (two asked providers)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: mapper stores only the first provider. Prediction: only test 4 fails. **Actual: as predicted.** Restored; green (4 pass, 9 skipped).

## Cycle 7 — repository test 5 (answered request with Grandma's Help)
- Fixture: HELP_ID, FRIDAY_18_10, BUTTER_WITH_THREE_PHOTOS, grandmaExplainsStep4.
- Prediction (Red): fails on helps().singleElement() "Expected size: 1 but was: 0"; status and provider side already round-trip; tests 1–4 green; 8 skipped. **Actual: as predicted.**
- Finding for Green: the adapter cannot rebuild a Help — `Help`'s constructor is package-private (domain package) and there is no public reconstitution path for the entity. Defect of step 6 (aggregate shape: reconstitution path) → route back to step 6 with a new failing test there.
- (step 6 r1 done, committed 275ec58.)
- Green: V1 help columns (position, provider_type, provider_person, answer_title, answer jsonb, accepted); HelpRow child list; AnswerJson + converters (PersistenceConfiguration extends AbstractJdbcConfiguration); AnswerJsonMapper (AsyncAPI field names); mapper via Help.reconstitute. postgresql driver scope runtime → compile (PGobject). Prediction: test 5 passes, 8 skipped. **Actual: as predicted.**
- Refactor: no change; answer serialization already its own unit (changes per answer type). Domain leak check clean.

## Cycle 8 — repository test 6 (Help from Jonas keeps his person id)
- Fixture: OTHER_HELP_ID, JONAS, MIA, communityMemberExplainsStep4.
- Prediction: green on activation. **Actual: as predicted.**
- Trial: person not stored. Prediction: only test 6 fails. **Actual: as predicted.** Restored; green (6 pass, 7 skipped).

## Cycle 9 — repository test 7 (loaded request has no pending events)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: reconstitute records HelpRequested (fast suite skipped for this run only). Prediction: only test 7 fails. **Actual: as predicted.** Restored; 30 fast + 7 repository ITs green.

## Cycle 10 — repository test 8 (version 1, then 2)
- Prediction (Red): "expected 1L but was 0L" (null Long version starts at 0). **Actual: as predicted.**
- Green: primitive `long` version in HelpRequestRow; mapper passes the aggregate's version (0 = never stored → insert stores 1; N → update WHERE version = N stores N+1). Prediction: test 8 passes, 5 skipped. **Actual: as predicted.**
- Refactor: no change; boundary candidate none.

## Cycle 11 — repository test 9 (optimistic locking)
- Prediction (Red): wrong throwable — Spring's OptimisticLockingFailureException (possibly wrapped). **Actual: as predicted** (OptimisticLockingFailureException, unwrapped, from updateWithVersion).
- Green: save catches OptimisticLockingFailureException → ConcurrencyConflict(id). Prediction: test 9 passes, 4 skipped. **Actual: as predicted.** (Twice-in-a-row run at the unit checkpoint — concurrency.)
- Refactor: no change; one translation inline at its call (separate translator only when uniqueness rules appear).

## Cycle 12 — repository test 10 (HelpRequestCreated in the outbox)
- Scaffold (schema, to reach a behavioral Red): complete outbox in V1 — event columns, delivery columns with defaults (attempts 0, next_attempt_at '-infinity'), partial claim index outbox_claim (aggregate_id, id) where unpublished and not dead-lettered.
- Prediction (Red): "Expected size: 1 but was: 0"; tests 1–9 green; 3 skipped. **Actual: as predicted.**
- Green: @Transactional save; pulled HelpRequested → OutboxEventMapper (AsyncAPI HelpRequestCreated payload, Jackson JavaTimeModule, ISO instants) → insert into outbox. HelpProvided skipped until test 11. Prediction: test 10 passes, 3 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axes — aggregate state storage vs outbox (shared with the relay, any aggregate type). Move: `Outbox.append(aggregateType, aggregateId, event)` component. Prediction green; **actual: green**. Retained.

## Cycle 13 — repository test 11 (HelpProvided in the outbox)
- Prediction (Red): "Expected size: 1 but was: 0" for HelpProvided rows; tests 1–10 green; 2 skipped. **Actual: as predicted.**
- Green: exhaustive switch over the sealed HelpRequestEvent; HelpProvided → AsyncAPI payload (answer via AnswerJsonMapper.toPayload, helpProvider omitted when absent); filter removed. Prediction: test 11 passes, 2 skipped. **Actual: as predicted.**
- Refactor: no change (answer JSON shape defined once; exhaustive switch forces mapping of new events).

## Cycle 14 — repository test 12 (failed save leaves nothing)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: outbox append before state, no @Transactional. Prediction: only test 12 fails (extra HelpProvided row). **Actual: as predicted.** Restored; green (12 pass, 1 skipped).

## Cycle 15 — repository test 13 (second Help, no orphans)
- Prediction: green on activation. **Actual: as predicted.** All 13 repository ITs active.
- Trial: mapper stores only the first Help. Prediction (final): only test 13 fails (wording wavered over test 9 before settling — noted). **Actual: as predicted.** Restored; 13/13 green.

## Cycle 16 — UnitOfWorkIT test 3 (repository save joins the unit of work)
- UoW test cleanup extended to the aggregate tables and outbox.
- Prediction: green on activation (REQUIRED joins). **Actual: as predicted.**
- Trial: save with REQUIRES_NEW. Prediction: only UoW test 3 fails. **Actual: as predicted.** Restored; 3/3 green. Unit 1 complete.

## Cycle 17 — ProcessedMessagesIT test 1 (first processing recorded)
- Scaffold: port application.ProcessedMessages (recordFirstProcessing(UUID)); adapter JdbcProcessedMessages returning false.
- Prediction (Red): "Expecting value to be true but was false"; 2 skipped. **Actual: as predicted.**
- Green (smaller than announced): plain insert, return true; `on conflict` left for test 2. Prediction: test 1 passes, 2 skipped.
  **Actual: as predicted.** Refactor: no change; boundary candidate none.

## Cycle 18 — ProcessedMessagesIT test 2 (repeated id → false)
- Prediction (Red): second call errors with DuplicateKeyException on processed_message_pkey before the assertion; test 1 green; 1 skipped. **Actual: as predicted.**
- Green: on conflict (message_id) do nothing; return inserted == 1. Prediction: 2 pass, 1 skipped.
  **Actual: as predicted.** Refactor: no change (one atomic statement; boundary candidate none).

## Cycle 19 — ProcessedMessagesIT test 3 (rolled back with its unit of work)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: recordFirstProcessing with REQUIRES_NEW. Prediction: only test 3 fails. **Actual: as predicted.** Restored; 3/3 green.
- All 19 ITs of step 7 active and green → refactoring points B3-S01 and B3-S02 (step 7).

## Slice refactoring — B3-S01, step 7 (EXACT refactor agent, fresh)
- Snapshot saved (scratchpad refactor-B3-S01-step7/). Report complete.
- Changes: OutboxEvent moved to its own type (Outbox no longer depends on OutboxEventMapper); import order fixed. Not applied: splitting save (atomic order + transaction clearer together).
- Proposals: (1) OutboxEventMapper assumes recipe/howToStep non-null — spec decision if other types allow none (inc 3: catastrophe recipe optional!); (2) shared JSON setup for both mappers → B3-S02 review.
- Verified: tests unchanged (diff); only Outbox, OutboxEventMapper, new OutboxEvent differ. Prediction: surefire 30, failsafe 19 green. **Actual: as predicted.**
- Decision (user): keep. Open item → inc 3: optional recipe in HelpRequestCreated mapping.

## Slice refactoring — B3-S02, step 7 (EXACT refactor agent, fresh)
- Snapshot saved (scratchpad refactor-B3-S02-step7/). Report complete. Change: AsyncApiJson — one module JSON setup (JavaTimeModule, ISO times) shared by AnswerJsonMapper and OutboxEventMapper. Rejected: splitting Help mapping out of HelpRequestMapper (cohesive aggregate mapping).
- Verified: tests unchanged; only AnswerJsonMapper, OutboxEventMapper, new AsyncApiJson differ. Prediction: green twice. **Actual: green twice** (surefire 30, failsafe 19).
- Documentation item: ADR0009 step-7 addition — stored answers and outbox payloads follow one JSON rule set.
- Unit approved (user): keep AsyncApiJson; commits 97e3cff (feat, verified pre-agent state), e053de1 (refactor, verified).
