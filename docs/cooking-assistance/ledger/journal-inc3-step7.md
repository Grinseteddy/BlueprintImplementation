# Journal — increment 3, step 7 (Persistence), unit Help Request

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01, B4-S02, plus B3-S06 per side (step 3 r21). EXACT Coding at 88fb3f9, autonomy full-hitl. Database: PostgreSQL 16 on Testcontainers. Inputs: reuse steps 4, 5, 6 (user). Fast loop: `OutboxEventMapperTest`, the repository contract on the fake; unit IT `HelpRequestCatastropheRepositoryIT`.

## Test list
- Decisions (user, 2026-11-13): pictures in `help_request.pictures text[]`; `HelpRequestCreated.pictures` absent when there are none (as `howToStep`); the approved contract test `aRequestWithARunningSideIsNotDue` contradicts r21 and is replaced by `aRequestWithOneSideDueIsDue`, plus `aRequestWhoseOnlyPassedSideTimedOutIsNotDue`.
- Found while drafting: `HelpRequestCreated` reads `recipe().value()` unconditionally (a catastrophe request without a recipe fails on save); the claim query needs every deadline passed (r21: one due side).
- 16 items: `OutboxEventMapperTest` 5, `HelpRequestCatastropheRepositoryIT` 8, `HelpRequestRepositoryContract` 3. Fixtures `lenaAsksAboutBurntChicken`, `grandmaMitigates` in `HelpRequests`. Inactive list compiles; fast suite green, 8 skipped (5 + 3 on the fake), as predicted.
- A5 Green: process slip — the check ran before the prediction was stated (it would have been: A5 green, fast suite green); result green.

## Cycles
- A1 Red (no `pictures` in HelpRequestCreated) → Green: `pictures(...)`, absent when empty. A2 Red (NPE `recipe().value()`) → Green: recipe null-safe (INV-19). A3 green on activation; trial (empty list published) failed A3 and the two other HelpRequestCreated field tests, as predicted. A4 Red (placeholder) → Green: generated `CatastropheMitigation` in `AnswerMessageMapper`. A5 Red (NPE) → Green: recipe null-safe (INV-37).
- C1 Red on the fake (pictures `[]`), as predicted. **Paused by the user 2026-11-13** before C1's Green (proposed: `reconstitute` gains `pictures`, the fake's Snapshot keeps them, `HelpRequestMapper` passes `List.of()` until V10; check `JdbcHelpRequestQueries` for a `reconstitute` call).
- Resumed 2026-11-14. C1 Green: new 16-arg `reconstitute` with pictures (the 15-arg one delegates with `List.of()`, no approved test changed); the fake keeps pictures. Green on the fake as predicted; red on JDBC until B2.
- B1 Red (no `pictures` column), as predicted → Green: `V10__help_request_pictures.sql` (`text[] not null default '{}'`); B1 green and fast suite green (2 skipped: C2, C3 on the fake), as predicted.
- B2 Red (pictures `[]` from JDBC: no row field, mapper uses the 15-arg `reconstitute`), as predicted.
- B2 Green attempt: **prediction mismatch** — pictures now round-trip and C1 passes on JDBC, but B2 fails at `askedProviders`: `containsExactly(COMMUNITY, GRANDMA)` vs stored [GRANDMA, COMMUNITY]. Cause: the test, not the adapter — `requestHelpWithCatastrophe` takes a `Set` (fixture `Set.of(providers)`), so the side order is unspecified; JDBC keeps the saved order. Stopped for the user.
- B2 test fixed (user, 2026-11-14): `askedProviders` asserted `containsExactlyInAnyOrder` (the sides come from a `Set`). B2 Green: `HelpRequestRow.pictures` (`List<String>` ↔ `text[]`), `HelpRequestMapper` both ways, 16-arg `reconstitute`. B2, C1 on JDBC, `HelpRequestRepositoryIT`, `HelpRequestIngredientRepositoryIT`, fast suite green, as predicted.
- C1 (JDBC) trial: `toDomain` with `List.of()` → B2 and C1 failed on pictures (`[]`), nothing else; restored. As predicted.
- B3 green on activation, as predicted; trial (`toDomain` always `new RecipeId(row.recipe())`) failed B3 only (`RecipeId[value=null]`), as predicted; restored.
- B4 (outbox) green on activation, as predicted; trial (`OutboxEventMapper.pictures` returns null) failed B4 only, at pictures; restored.
- B5 Red (`IllegalStateException: catastrophe answers are not stored yet` from `AnswerJsonMapper.toJson`), as predicted.
- B5 Green: `CatastropheMitigationJson`, `toPayload`/`toMitigation`, `toAnswer` as a switch on answerType; B5, catastrophe/ingredient/repository/contract ITs, fast suite green, as predicted.
- B6 Red (NPE `recipe().value()` in `AnswerJsonMapper.toPayload(CatastropheMitigation)`), as predicted.
- B6 Green: recipe null-safe both ways in `AnswerJsonMapper` (INV-37); B6 and all persistence ITs, fast suite green, as predicted.
- B7 green on activation, as predicted; trial (`toDomain(AskedProviderRow)` always ASKED) failed B7 only; restored.
- B8 green on activation, as predicted; trial (domain `timeOut` always times out the whole request) failed B8 (outbox [HelpRequestCreated, HelpRequestTimedOut]) and B7 (CANCELLED), as predicted; restored.
- C2 activated, `aRequestWithARunningSideIsNotDue` deleted (decided 2026-11-13); setup as helper `hollandaiseWithCommunity(state)`. C2 Red on the fake and on JDBC (`[]`), as predicted.
- C2 Green: fake `anyMatch(now.isAfter(deadline))`; claim query `exists (… a.deadline < :now)`; C2 on fake and JDBC, all persistence ITs, fast suite green, as predicted.
- C3 Red on the fake and on JDBC (request returned although its only passed side is TIMED_OUT), as predicted. All 16 items now active.
- C3 Green: side state ASKED in the fake and the claim query (r21); C3 on fake and JDBC, all persistence ITs, fast suite green (0 skipped), as predicted. All 16 items green.

## Refactoring
- Isolated refactor agent (one, after C3). Tests unchanged (`diff -r` against the snapshot taken before: empty). Kept by the user 2026-11-14: (1) `AnswerJsonMapper` constant `PREPARATION_STEP_EXPLANATION` and a label on the `toAnswer` default branch; (2) claim-query comment names INV-26 and `AskedProvider.isDueAt`. Fast suite, persistence ITs, `spotless:check` green per the agent; diffs inspected.
- Domain boundary: the due-side rule lives in the claim SQL (pre-filter under `skip locked`) and in `AskedProvider.isDueAt` (authoritative); not movable, linked by comment. Nothing misplaced.
- Proposals not applied: explicit `PREPARATION_STEP_EXPLANATION` case with a throwing default for an unknown stored answerType (behavior change, test-first); retire the 15-arg `reconstitute` (needs test edits); optional IT pinning SQL vs `isDueAt` boundaries.
- Unknown stored answerType proposal: to the ledger's planned list (user, 2026-11-14).

## Finish
- 16/16 active and green: `OutboxEventMapperTest` 5, `HelpRequestCatastropheRepositoryIT` 8, `HelpRequestRepositoryContract` 3 (fake and JDBC). Database: PostgreSQL 16 on Testcontainers.
- Prediction mismatch, behavioral: 1 (B2 — side order asserted although the domain takes a `Set`; test fixed with the user's approval). Process slip: 1 (A5).
- `./mvnw -o verify` 2026-11-14: BUILD SUCCESS (fast 548, slow 199 + larder-app, Spotless, Error Prone, Spectral; info only: asyncapi-latest-version), as predicted; second run for the claim query (skip locked): BUILD SUCCESS again, as predicted.
- Leak checks: domain imports no infrastructure — none; domain/application/fakes/contract import no adapter — none; migrations: only V10 new, none edited.
- Schema: V10 `help_request.pictures text[] not null default '{}'` (no constraint: INV-20/INV-38 0..10 stay in the domain). Outbox unchanged.

## Handed on
- Step 8: the time-out poller now gets requests with one due side (r21); the use case must save after a side-only pass (no event, version + 1).
- Step 9/10: `HelpRequestCreated.pictures` and the `CatastropheMitigation` answer in `HelpProvided` are mapped; publishing unchanged.
- Step 11: `HelpMapper` placeholder for catastrophe answers; read side does not show pictures yet; `JdbcHelpRequestQueries` does not read `pictures`.
- Planned list: unknown stored answerType → explicit rejection (test-first); retire the 15-arg `reconstitute` (test edits).
