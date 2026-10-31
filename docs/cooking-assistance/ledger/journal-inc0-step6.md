# Journal — increment 0, step 6 (Domain model), unit Help Request

## Test list
- Setup: parent POM `larder-parent`, module `cooking-assistance` (JUnit 5, AssertJ, jqwik 1.9.2 via Spring Boot 3.5.6 BOM), Maven wrapper 3.9.9. JDK 21 release (runtime JDK 23).
- Process slip: the first `./mvnw test` with the inactive list was run **without a prediction**. Result: BUILD SUCCESS, 26 Jupiter tests skipped, 0 jqwik properties discovered. Not counted as evidence.
- Finding from that run: jqwik does not discover `@Property` methods in Jupiter `@Nested` classes, and needs `net.jqwik.api.Disabled`. Fix (structure only): the 3 properties moved to the top-level `HelpRequestPropertiesTest`.
- Prediction: BUILD SUCCESS; 26 Jupiter tests skipped in 3 nested classes; 3 jqwik properties skipped; 0 failures, 0 errors. **Actual: as predicted** (29 skipped).

## Cycle 1 — test 1 (B3-S01 US-CA-03 INV-01 R1.E1, INV-02 R2.E1)
- Activated `requestToGrandmaIsOpenWithItsRecipeStepAndProvider`; @Disabled text kept as @DisplayName (trace preserved).
- Prediction (compile): testCompile fails with "cannot find symbol" for HelpRequest, HelpRequestId, CookId, RecipeId, HowToStepId, HelpProviderType, HelpRequestStatus, HelpType. **Actual: as predicted.**
- Scaffold: the eight types; enums only with the values the test names; factory returns an empty object, accessors return null / empty.
- Prediction (Red): compiles; test 1 fails with AssertionFailedError "expected: OPEN but was: null"; 25 Jupiter + 3 jqwik skipped. **Actual: as predicted** (Failures 1, Errors 0, Skipped 10+12+3+3).
- Green: factory stores requester, recipe, howToStep, providers; status() and type() return constants (intermediate). Prediction: test 1 passes, 28 skipped. **Actual: as predicted.**
- Refactor: Four Rules — no change. Domain responsibility: records a cook's request for help with one step of one recipe from chosen providers. Axes: INV-01, INV-02, INV-09 (not in code yet). Boundary candidate: none (one cohesive decision, no guard yet). Leak check: no hits.

## Cycle 2 — test 2 (B3-S01 US-CA-03 evt HelpRequested)
- Scaffold: sealed `HelpRequestEvent`, record `HelpRequested` (AsyncAPI HelpRequestCreated fields), record `AskedProvider(providerType, deadline)`, `pullEvents()` returning an empty list.
- Prediction (Red): compiles; test 2 fails on hasSize(1) "Expected size: 1 but was: 0"; test 1 green; 24 Jupiter + 3 jqwik skipped. **Actual: as predicted** (Failures 1, Skipped 27).
- Green: factory records HelpRequested (occurredAt from the clock; deadline null until test 3); pullEvents returns a copy (clearing waits for test 24). Prediction: tests 1–2 pass, 27 skipped. **Actual: as predicted.**
- Refactor A (tests): helper `lenaAsksGrandmaAboutStep4()`. Prediction: green. **Actual: as predicted.**
- Refactor B (boundary trial): Domain responsibility — records a cook's request for help with one step of one recipe, addressed to chosen providers, each asked until a deadline. Axes: INV-01 (who may be asked), INV-09 (until when). Candidate: consolidate "asked providers" as owned state (List<AskedProvider>); type and status as fields. Prediction: green, compile passes. **Actual: as predicted.** Outcome: retained — the provider list exists once, the event and the accessor read the same state; constants OPEN / PREPARATION_STEP_EXPLANATION appear once. Fewest elements: no new element, one field replaced.

## Cycle 3 — test 3 (B3-S01 US-CA-03 INV-09 R8.E2, deadline only)
- Scaffold: accessor `askedProviders()` exposing existing state.
- Prediction (Red): compiles; test 3 fails in containsExactly, actual deadline=null vs expected 2026-11-07T05:00:00Z; tests 1–2 green; 26 skipped. **Actual: as predicted** (Failures 1).
- Green: deadline = clock instant + 12 h. Prediction: tests 1–3 pass, 26 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axis INV-09 (answer time per type) vs INV-01; candidate: named constant PREPARATION_STEP_ANSWER_TIME with INV-09 trace (policy class premature with one type). Prediction: green.
  **Actual: as predicted.** Outcome: retained — the 12 h rule is named and traced; a change of the answer time is found in one place. Fewest elements: one constant, needed to reveal INV-09.

## Cycle 4 — test 4 (B3-S01 US-CA-03 INV-01 R1.E2)
- Test helper `lenaAsksAboutStep4(providers...)`; `lenaAsksGrandmaAboutStep4()` delegates.
- Prediction (compile): "cannot find symbol: variable COMMUNITY". **Actual: as predicted.** Scaffold: enum value COMMUNITY.
- Prediction: test 4 green on activation (generalization from cycles 2–3). **Actual: as predicted.** No production change.
- Trial: `limit(1)` in provider mapping. Prediction: only test 4 fails (missing element). **Actual: as predicted.** Restored; prediction green (4 passed, 25 skipped).

## Cycle 5 — test 5 (B3-S01 US-CA-03 INV-01 R1.E3)
- Test constants TOM, BREAD, STEP_2, OTHER_HELP_REQUEST_ID.
- Prediction: compiles, green on activation (no premium concept in the domain). **Actual: as predicted.** No production change.
- Trial: reject Community-only requests. Prediction: only test 5 errors with IllegalStateException. **Actual: as predicted.** Restored; prediction green (5 passed, 24 skipped).

## Cycle 6 — test 6 (B3-S01 US-CA-03 INV-01 R1.E4)
- Scaffold: enum value CHEF; abstract unchecked `DomainRejection(rule, code, message)`; `ChefNotAllowed` (INV-01, CHEF_NOT_ALLOWED).
- Prediction (Red): compiles; test 6 fails "Expecting code to raise a throwable."; tests 1–5 green; 23 skipped. **Actual: as predicted.**
- Green: guard before creation, CHEF among providers → ChefNotAllowed. Prediction: tests 1–6 pass, 23 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axis INV-01 (who may be asked; grows with "nobody" and increment 4) vs INV-09; candidate: named predicate `ensureProvidersMayBeAsked`. Prediction: green.
  **Actual: as predicted.** Outcome: retained — the INV-01 policy has one named home; the factory reads guard → state → event. Fewest elements: one private method, needed to name the rule.

## Cycle 7 — test 7 (B3-S01 US-CA-03 INV-01 R1.E4)
- Prediction: green on activation (guard uses contains). **Actual: as predicted.** No production change.
- Trial: guard narrowed to equals(Set.of(CHEF)). Prediction: only test 7 fails. **Actual: as predicted.** Restored; prediction green (7 passed, 22 skipped).

## Cycle 8 — test 8 (B3-S01 US-CA-03 INV-01 glossary 1..2)
- Reading: no dedicated spec code; the API schema answers 400 VALIDATION_FAILED for an empty list, so the domain rejection uses VALIDATION_FAILED (existing code, nothing invented).
- Scaffold: `NoHelpProviderChosen` (INV-01, VALIDATION_FAILED).
- Prediction (Red): compiles; test 8 fails "Expecting code to raise a throwable."; tests 1–7 green; 21 skipped. **Actual: as predicted.**
- Green: empty provider set → NoHelpProviderChosen in ensureProvidersMayBeAsked. Prediction: tests 1–8 pass, 21 skipped. **Actual: as predicted.**
- Refactor: comment states the whole INV-01 (1..2, never a Chef). Boundary candidate: none — both checks are INV-01 (one card, glossary 1..2), one change axis. Prediction green; **actual: green** (8 passed, 21 skipped).

## Cycle 9 — test 9 (B3-S01 US-CA-03 INV-02 recipe)
- Scaffold: `RecipeRequired` (INV-02, MISSING_FIELD).
- Prediction (Red): compiles; test 9 fails "Expecting code to raise a throwable."; tests 1–8 green; 20 skipped. **Actual: as predicted.**
- Green: null recipe → RecipeRequired. Prediction: tests 1–9 pass, 20 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axes INV-01 (providers) vs INV-02 (content per type; grows in inc 2/3); candidate: predicate `ensureOneStepOfOneRecipe(recipe, howToStep)`. Prediction green; **actual: green**. Outcome: retained — each creation rule has its own named home. Fewest elements: one private method; `howToStep` parameter unused until test 10 (name kept for the rule).

## Cycle 10 — test 10 (B3-S01 US-CA-03 INV-02 step)
- Scaffold: `PreparationStepRequired` (INV-02, MISSING_FIELD).
- Prediction (Red): compiles; test 10 fails "Expecting code to raise a throwable."; tests 1–9 green; 19 skipped. **Actual: as predicted.**
- Green: null step → PreparationStepRequired inside ensureOneStepOfOneRecipe. Prediction: tests 1–10 pass, 19 skipped. **Actual: as predicted.**
- Refactor: no change. Boundary candidate: none — both checks implement card US-CA-03 R2 (one numbered step of one recipe); same change axis.

## Cycle 11 — test 11 (B3-S01 US-CA-03 INV-02 R2.E2)
- Observation: each request keeps its own step and its own given id (different ids). Scaffold: accessor `id()` returning null.
- Prediction (Red): compiles; test 11 fails on aboutStep4.id() expected HelpRequestId[23a8eeed…] but was null; tests 1–10 green; 18 skipped. **Actual: as predicted.**
- Green: id stored in the aggregate; event reads request.id. Prediction: tests 1–11 pass, 18 skipped. **Actual: as predicted.**
- Refactor: no further change; boundary candidate none (identity is state, not a policy).

## Property P1 (B3-S01 US-CA-03 INV-01 property)
- jqwik 1.9.2, @Property(tries = 1000, seed = "20261031"); generation EXHAUSTIVE (7 non-empty subsets) — stronger than random tries.
- Prediction: green on activation. **Actual: as predicted.** No production change; no counterexample, no candidate example for step 3.
- Trial: limit(1). Prediction: P1 fails with a two-provider counterexample, test 4 fails. **Actual: as predicted** (sample [GRANDMA_AVATAR, COMMUNITY]). Restored; green (12 passed, 17 skipped).

## Property P2 (B3-S01 US-CA-03 INV-09 property, deadline only)
- Generator: epoch millis 2020-01-01 … 2040-01-01 (not hour-aligned); seed 20261031; 1000 tries.
- Prediction: green on activation. **Actual: as predicted.** No production change.
- Trial: deadline truncated to hours. Prediction: P2 fails with a non-hour-aligned counterexample; example test 3 (17:00:00Z) still passes. **Actual: as predicted** (shrunk to 1577836800001). Restored; green (13 passed, 16 skipped).
- B3-S01 fully green in step 6 → refactoring point.

## Slice refactoring — B3-S01, step 6 (EXACT refactor agent, fresh, agent type `refactor`)
- Snapshot + patch saved before start (scratchpad refactor-B3-S01-step6/).
- Agent report complete (Four Rules, changes, boundary outcome, predictions, final result). Changes: (1) one clock read `requestedAt` for deadline and occurredAt (Rule 3); (2) private `askPreferredProviders` with INV-09 trace (Rule 2, boundary retained). No test files changed (verified by diff); traces preserved.
- Orchestrator check — prediction: green, 13 passed, 16 skipped. **Actual: as predicted.**
- Side finding: my `git add -N` had put target/ and .jqwik-database in the index → index reset, `.gitignore` added (target/, .jqwik-database, .idea/).
- Decision (user): keep both agent changes; separate refactor commit after the unit commit.

## Cycle 12 — test 12 (B3-S02 US-CA-03 INV-04 R4.E1)
- Scaffold: HelpId, ProviderState {ASKED, ANSWERED}, HelpProvider(type, Optional<CookId>) with grandmaAvatar(), PreparationStepExplanation(recipe, howToStep, description, images), entity Help (accessors only), AskedProvider + state (2-arg constructor defaults ASKED, so approved tests 3/4 stay unchanged), HelpRequestStatus ANSWERED, no-op provideHelp, helps() empty.
- Prediction (Red): compiles; test 12 fails "expected: ANSWERED but was: OPEN"; tests 1–11, P1, P2 green; 15 skipped. **Actual: as predicted.**
- Green: provideHelp adds Help, status ANSWERED, answering provider's side ANSWERED. Prediction: 14 passed, 15 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axes — request lifecycle vs per-provider state (timeouts in inc 1). Candidate: `AskedProvider.answered()` (owning concept) + trace comment on provideHelp. Prediction green; **actual: green**. Outcome: retained. Fewest elements: one package-private method replaces an inline constructor call.

## Cycle 13 — test 13 (B3-S02 US-CA-03 evt HelpProvided)
- Scaffold: record `HelpProvided` (AsyncAPI HelpProvided fields), added to the sealed interface.
- Prediction (Red): compiles; test 13 fails "Expected size: 1 but was: 0"; earlier tests green; 14 skipped. **Actual: as predicted.**
- Green: provideHelp records HelpProvided (occurredAt from the clock). Prediction: 15 passed, 14 skipped. **Actual: as predicted.**
- Refactor (tests): constant BUTTER_IN_A_THIN_STREAM + helper grandmaExplainsStep4(request). Prediction green; **actual: green**. Production unchanged; boundary candidate none (event recording belongs to the command).

## Cycle 14 — test 14 (B3-S02 US-CA-03 INV-04 R4.E1, pictures)
- Test helper photos(n).
- Prediction: green on activation. **Actual: as predicted.** No production change.
- Trial: Help drops images. Prediction: only test 14 fails. **Actual: as predicted.** Restored; green (16 passed, 13 skipped).

## Cycle 15 — test 15 (B3-S02 US-CA-03 INV-04 R4.E3)
- Scaffold, written directly (plain value constructor, no rule): `HelpProvider.community(member)`. Test constants JONAS, MIA, OTHER_HELP_ID.
- Prediction: green on activation. **Actual: as predicted.** No production behavior change.
- Trial: ignore answers when ANSWERED. Prediction: only test 15 fails. **Actual: as predicted.** Restored; green (17 passed, 12 skipped).

## Cycle 16 — test 16 (B3-S02 US-CA-03 INV-38 boundary 10)
- Test helper explanationWith(pictures).
- Prediction: green on activation. **Actual: as predicted.**
- Trial: off-by-one limit `>= 10`. Prediction: only test 16 errors. **Actual: as predicted.** Restored; green (18 passed, 11 skipped).

## Cycle 17 — test 17 (B3-S02 US-CA-03 INV-38 S3.E3)
- Scaffold: `TooManyPictures` (INV-38, TOO_MANY_PICTURES).
- Prediction (Red): compiles; test 17 fails "Expecting code to raise a throwable."; earlier green; 10 skipped. **Actual: as predicted.**
- Green: guard `> 10` images → TooManyPictures before any change. Prediction: 19 passed, 10 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axis INV-38 (pictures, also on catastrophe requests in inc 3) vs provider/matching rules. Move: predicate ensureAtMostTenPictures + constant MAX_PICTURES (INV-38 traces). Prediction green; **actual: green**. Retained. Considered, not tried: `Pictures` value object — premature with one use; trigger in inc 3.

## Cycle 18 — test 18 (B3-S02 US-CA-03 INV-36 S3.E1)
- Scaffold: `OwnRequest` (INV-36, OWN_REQUEST).
- Prediction (Red): compiles; test 18 fails "Expecting code to raise a throwable."; earlier green; 9 skipped. **Actual: as predicted.**
- Green: answering person == requester → OwnRequest. Prediction: 20 passed, 9 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axis INV-36 (actor rule, D3) vs INV-38/INV-37. Move: predicate ensureNotOwnRequest(provider). Prediction green.
  **Actual: green.** Retained — provideHelp reads as named rules (INV-36, INV-38) before the state change.

## Cycle 19 — test 19 (B3-S02 US-CA-03 INV-37 S3.E2, step)
- Scaffold: `AnswerDoesNotMatchRequest` (INV-37, ANSWER_DOES_NOT_MATCH_REQUEST).
- Prediction (Red): compiles; test 19 fails "Expecting code to raise a throwable."; earlier green; 8 skipped. **Actual: as predicted.**
- Green: answer step ≠ request step → AnswerDoesNotMatchRequest. Prediction: 21 passed, 8 skipped. **Actual: as predicted.**
- Refactor (boundary trial): axis INV-37 (grows: recipe in test 20, type in inc 2). Move: predicate ensureAnswerMatchesRequest(answer). Prediction green.
  **Actual: green.** Retained.

## Cycle 20 — test 20 (B3-S02 US-CA-03 INV-37 recipe dimension)
- No scaffold needed.
- Prediction (Red): compiles; test 20 fails "Expecting code to raise a throwable." (same step id, only recipe differs); earlier green; 7 skipped. **Actual: as predicted.**
- Green: recipe compared too. Prediction: 22 passed, 7 skipped. **Actual: as predicted.**
- Refactor (boundary trial): "what an answer is about" owned by the answer → PreparationStepExplanation.isAbout(recipe, step). Prediction green.
  **Actual: green.** Retained — each answer type will own its "about" (ingredient in inc 2, optional recipe in inc 3); the aggregate states only the rule.

## Cycle 21 — test 21 (B3-S02 US-CA-03 brief precondition, spec PROVIDER_NOT_ASKED)
- Reading: no INV names it alone; anchored to INV-01 (the request goes to the chosen providers). Test checks only the spec code.
- Scaffold: `ProviderNotAsked` (INV-01, PROVIDER_NOT_ASKED).
- Prediction (Red): compiles; test 21 fails "Expecting code to raise a throwable."; earlier green; 6 skipped. **Actual: as predicted.**
- Green: provider type not asked → ProviderNotAsked. Prediction: 23 passed, 6 skipped. **Actual: as predicted.**
- Refactor: predicate ensureProviderWasAsked(provider). Watch item: provider matching by type appears twice (guard, marking answered); kept — inc 4 (Chef picked by name) changes both together. Prediction green.
  **Actual: green.** Retained.

## Cycle 22 — test 22 (B3-S02 US-CA-03 lifecycle, CANCELLED)
- Scaffold: HelpRequestStatus CLOSED, CANCELLED; `HelpRequest.reconstitute(id, requester, title, description, type, recipe, howToStep, askedProviders, status, helps, version)` — passes values to the constructor; title/description/version stored when lifecycle test 25 asks; `InvalidHelpRequestState(status)` (rule label LIFECYCLE — no INV id; test checks only INVALID_STATE). Test helper storedRequestToGrandmaIn(status).
- Prediction (Red): compiles; test 22 fails "Expecting code to raise a throwable."; earlier green; 5 skipped. **Actual: as predicted.**
- Green: CANCELLED → InvalidHelpRequestState, first guard of provideHelp. Prediction: 24 passed, 5 skipped. **Actual: as predicted.**
- Refactor (boundary trial): lifecycle axis (shared with cancel/accept/timeout in inc 1). Move: predicate ensureOpenForAnswers(). Prediction green.
  **Actual: green.** Retained.

## Cycle 23 — test 23 (B3-S02 US-CA-03 lifecycle, CLOSED)
- Test helper storedRequestIn(status, askedProvider); request asked of the Community so the lifecycle is the only reason.
- Prediction (Red): compiles; test 23 fails "Expecting code to raise a throwable."; earlier green; 4 skipped. **Actual: as predicted.**
- Green: CLOSED also → InvalidHelpRequestState. Prediction: 25 passed, 4 skipped. **Actual: as predicted.**
- Refactor (boundary trial): "final states" owned by the status → HelpRequestStatus.isFinal(); reused by inc 1 commands. Prediction green.
  **Actual: green.** Retained.

## Property P3 (B3-S02 US-CA-03 INV-38 property)
- Generator: n in 0..30; seed 20261031; tries 1000 (int range small → effectively exhaustive).
- Prediction: green on activation. **Actual: as predicted.** No counterexample → no candidate example for step 3.
- Trial: MAX_PICTURES = 9. Prediction: P3 fails at n = 10, test 16 fails, test 17 passes. **Actual: as predicted.** Restored; green (26 passed, 3 skipped).
- B3-S02 fully green in step 6 → refactoring point.

## Slice refactoring — B3-S02, step 6 (EXACT refactor agent, fresh, agent type `refactor`)
- Snapshot saved before start (scratchpad refactor-B3-S02-step6/).
- Agent report complete. Change: private `markSideAnswered(provider)` (per-provider state axis). Rejected by agent: INV-38 into the answer (would change when the rejection is raised; property builds answer outside assertThatThrownBy; Pictures VO planned inc 3); grouping guards (no gain). Proposals: `AskedProvider.isFor(HelpProvider)` in inc 4; INV-38 into Pictures VO in inc 3.
- Verified: tests unchanged (diff); production diff = the extraction only. Prediction: green 26 passed, 3 skipped. **Actual: as predicted.**
- Decision (user): keep markSideAnswered.

## Cycle 24 — test 24 (aggregate shape: pullEvents once)
- Prediction (Red): compiles; test 24 fails on the second pull "Expecting empty but was [HelpRequested…]"; earlier green; 2 skipped. **Actual: as predicted.**
- Green: pullEvents copies and clears. Prediction: 27 passed, 2 skipped. **Actual: as predicted.**
- Refactor: no change (Javadoc added with the Green). Boundary candidate: none — handing over pending events is the aggregate's own mechanism, not a policy.

## Cycle 25 — test 25 (reconstitution restores state)
- Scaffold: accessors title(), description(), version() returning null / 0.
- Prediction (Red): compiles; test 25 fails on title() expected "How do I keep the sauce from splitting?" but was null; earlier green; 1 skipped. **Actual: as predicted.**
- Green: title, description, version stored (factory version 0; reconstitute passes the stored version). Prediction: 28 passed, 1 skipped. **Actual: as predicted.**
- Refactor (Rule 3): HelpRequested built entirely from aggregate state. Prediction green; **actual: green**. Boundary candidate: none (reconstitution is the aggregate's own path).

## Cycle 26 — test 26 (reconstitution runs no guards)
- Prediction: green on activation. **Actual: as predicted.**
- Trial: reconstitute runs ensureProvidersMayBeAsked. Prediction: only test 26 errors with ChefNotAllowed. **Actual: as predicted.** Restored; green (29 passed, 0 skipped).
- Refactor (tests, mechanical): unused Disabled imports removed. Prediction green; **actual: green**.
- All 29 tests active and green.
