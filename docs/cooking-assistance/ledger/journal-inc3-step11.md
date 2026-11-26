# Journal — increment 3, step 11 (Query side)

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01, B4-S02 and B3-S06 (r21).
EXACT Coding at 88fb3f9, autonomy full-hitl. Input reused from steps 3 (INV-24 per r21), 4 (OpenAPI 1.0.16), 5 (ADR0016), 7 and 9 (user, 2026-11-16).

## Slice check
- ADR0016: the query side reads the Help Request tables (no projector); same transaction, no lag.
- Gaps found: HelpRequestView has no pictures (the HelpRequest response promises them); HelpMapper.toContractAnswer throws for a CatastropheMitigation (step 6 placeholder); previousNotes knows only INV-07 (same step) and INV-15 (same ingredient name), not INV-24 (earlier catastrophe requests of the same recipe; none without a recipe, step 3 r21).
- Spec drift: OpenAPI previousNotes description still says "notes about the same dish come with catastrophe help (planned)"; step 3 r21 noted it follows at step 11 → step 4 question to the user.
- Filters: type STEPS_TO_MITIGATE_CATASTROPHE already in the enums of listHelpRequests and listHelps.

## Test list
- 11 items: HelpRequestQueryIT 8, HelpQueryIT 3. Inactive list compiles; fast suite green.
- User 2026-11-16: test list approved; spec drift fixed as step 4 r25 (OpenAPI 1.0.17, previousNotes names INV-24; version header 1.0.17), CHANGELOG; Spectral clean, QS-15 gate exit 0, fast suite green, as predicted; r25 approved.

## Cycles
- Q1 Red: 200, schema, type and recipe pass; the pictures assertion fails with "Expecting actual: [] to contain exactly …" (HelpRequestView has no pictures), as predicted.
- Q1 Green: HelpRequestView.pictures; JdbcHelpRequestQueries selects pictures (text[] → List<URI>, helper with INV-20 comment); HelpRequestMapper sets pictures absent when empty. Query ITs green (43 run 7 skipped, 17 run 3 skipped) and fast suite green, as predicted.
- Q2 green on activation, as predicted (recipe read as RecipeId(null) → value null → absent; pictures absentWhenEmpty). Trial: mapper without absentWhenEmpty → body "pictures":[] and "… not to contain [recipe, pictures] but found [pictures]", as predicted; restored (cmp).
- Q3 green on activation, as predicted. Trial: list type filter "or true" → both requests listed, "containsExactly [catastrophe]" fails with the PSE id unexpected, as predicted; restored (cmp).
- Q4 green on activation, as predicted (step 7 stores the state per side; suggestionsAfterTimeOut empty while Community is ASKED → field absent). Trial: askedProviders reads every state as "ASKED" → "elements not found [(GRANDMA_AVATAR, TIMED_OUT)], not expected [(GRANDMA_AVATAR, ASKED)]", as predicted; restored (cmp).
- Q5 Red: 200 and schema pass; "[previousNotes] Expecting actual not to be null" — the notes query has no INV-24 condition (a catastrophe has no step or ingredient; null = null never matches), as predicted.
- Q5 Green: JdbcHelpRequestQueries gains ABOUT_A_CATASTROPHE_OF_THE_SAME_RECIPE (both STEPS_TO_MITIGATE_CATASTROPHE, same recipe; INV-24 comment), or-ed into the notes query, renamed NOTES_OF_EARLIER_REQUESTS_ABOUT_THE_SAME_STEP_INGREDIENT_OR_RECIPE. Query ITs green (HelpRequestQueryIT 3 and HelpQueryIT 3 still disabled) and fast suite green, as predicted.
- Q6 green on activation, as predicted (both recipes null; null = null never matches). Trial: "earlier.recipe is not distinct from read.recipe" → "[previousNotes] expected: null but was: [{CANCELLATION still tastes smoky …}]", as predicted; restored (cmp).
- Q7 green on activation, as predicted (the recipes differ). Trial: catastrophe condition without "earlier.recipe = read.recipe" → "[previousNotes] expected: null but was: [{CANCELLATION too salty …}]", as predicted; restored (cmp).
- Q8 green on activation, as predicted (the catastrophe condition needs both requests catastrophes; the step condition fails on the catastrophe's null step). Trial: catastrophe condition without "earlier.type = …" → "[previousNotes] expected: null but was: [{CANCELLATION oven too hot …}]", as predicted; restored (cmp).
- H1 Red: "Response status expected:<200> but was:<500>", cause IllegalStateException "catastrophe answers are not read yet" from the HelpMapper.toContractAnswer placeholder, as predicted.
- H1 Green: HelpMapper.toContractMitigation (generated CatastropheMitigation with explanation; recipe absent when none, images absentWhenEmpty) replaces the placeholder. Query ITs green (HelpRequestQueryIT all active; HelpQueryIT 2 still disabled) and fast suite green, as predicted.
- H2 green on activation, as predicted (recipe null → absent; images absentWhenEmpty). Trial: toContractMitigation with plain answer.images() → "Expecting [answerType, explanation, images] not to contain [recipe, images] but found [images]", as predicted; restored (cmp).
- H3 green on activation, as predicted (listHelps already filters r.type; H1 made the mitigation readable). Trial: "or true" on the r.type filter → Jonas's Help for Mia also listed, containsExactly fails, as predicted; restored (cmp).

## Refactor (isolated agent)
- Tests snapshotted (69 files, shasum) before; identical after. HelpRequestView and REST HelpRequestMapper unchanged by the agent.
- R1 HelpMapper.toContractMitigation: recipe as `answer.recipe() == null ? null : answer.recipe().value()` (the outbound mappers' idiom) instead of the Optional chain. Fast suite and query ITs green, as predicted. User 2026-11-16: keep.
- R2 notes query: `((INV-07) or (INV-15) or (INV-24))`, the three alternatives alike; same SQL. Fast suite and query ITs green, as predicted. User 2026-11-16: keep.
- Domain-boundary check: INV-24 stays a named SQL fragment beside INV-07 and INV-15 (ADR0016; SEC-14 bound and paging); no move.
- Not acted on: readById builds RecipeId(null) for a catastrophe without recipe (already on the planned list from inc 2 step 11; now also reached by catastrophes) → user 2026-11-16: planned list, together with the step 9 item (mitigation recipe as Optional<RecipeId>); the repeated 'STEPS_TO_MITIGATE_CATASTROPHE' literal and HelpType.name() — file idiom kept.

## Finish
- Checks: leak grep (domain, application) empty; no HelpRequestRepository in the query adapter or the query controllers (only Spring's @Repository stereotype on JdbcHelpRequestQueries); step 9 coverage gate = controllers implement the generated HelpRequestsApi/HelpsApi, compile green, no new operation in step 11 (r25 changes a description only); ifs/branches added: `case CatastropheMitigation` (contract type of the answer), `answer.recipe() == null ? null : …` (absent recipe, INV-37), the three SQL alternatives (which earlier requests are related: INV-07, INV-15, INV-24).
- clean verify run 1 failed on infrastructure: larder-app TimeOutTimelinessIT, RabbitMQ container start timed out ("Server startup complete" not seen); cooking-assistance fast 583 and slow 215 + 14 green.
- clean verify run 2 failed: AmqpAdaptersIT.publisherPutsTheMessageOnItsChannel received a real HelpRequestCreated (HELP_REQUEST_ID fixture) on its probe queue instead of its own payload. Likely (not verified): another cached test context's outbox poller (@Scheduled 1 s) publishes to the shared broker container while the probe queue is bound. [Corrected in step 12 A0: no other context shares the broker or runs the poller; the class's own poller published a row its earlier tests stored — reproduced and fixed there.] Untouched by step 11. User 2026-11-16: third run; the race goes on the planned list (test-first fix later).
- clean verify run 3: green (fast 583; slow 215 + 14; larder-app 69), BUILD SUCCESS.
