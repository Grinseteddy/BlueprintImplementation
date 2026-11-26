# Journal — increment 3, step 9 (Inbound adapters)

Board B4 "Help with a catastrophe", US-CA-04, slices B4-S01, B4-S02. EXACT Coding at 88fb3f9, autonomy full-hitl. Inputs: reuse steps 4 (OpenAPI 1.0.16, AsyncAPI 1.0.12), 5 (ADR0012–ADR0015, ADR0019, ADR0020), 8 (user, 2026-11-14). Generators unchanged (openapi-generator spring interfaceOnly, Modelina); no spec change.

## Slice check
- createHelpRequest type STEPS_TO_MITIGATE_CATASTROPHE → `RequestHelpWithCatastrophe` (today `default -> NotImplemented`, 501). The spec already enforces the catastrophe branch: no howToStep/ingredient/ingredientName (if/then), pictures ≤ 10 https links, CHEF refused by the else branch (400).
- provideHelp with a CatastropheMitigation → `ProvideHelp` (today `HelpMapper.toAnswer` default → 501); response is a link only (read side, `toContractAnswer` placeholder → step 11).
- consumeGrandmaHelpProvided with a CatastropheMitigation → `ProvideGrandmaAvatarHelp` (today `AnswerKindNotBuilt` → dead letter). AnswerTooLate → ignored with ACK (Q-A2), not dead-lettered.
- Error map unchanged: ChefNotAllowed etc. are caught by the schema first; AnswerDoesNotMatchRequest → 422 ANSWER_DOES_NOT_MATCH_REQUEST (INV-37).
- Drift found (step 8, approved and committed): test #12 `grandmaAnswersAfterHerSideTimedOut` says "(named outcome: dead-lettered with its rule, step 9)" and the step 8 journal hands on "AnswerTooLate from Grandma → dead-letter"; the consumer ignores late answers since Q-A2. The test's behavior is right (the handler propagates); its name and the journal line are wrong.

## Test list
- 15 items: HelpRequestsControllerTest 7, HelpsControllerTest 4, GrandmaHelpProvidedConsumerTest 3, InboundWiringIT 1 (W7). Inactive list compiles; fast suite green, 14 skipped, as predicted.
- Test list approved by the user 2026-11-14. Drift fixed (user): the name of `grandmaAnswersAfterHerSideTimedOut` and the step 8 Handed-on line now say "ignored with ACK, Q-A2".

## Cycles
- A1 Red (201 expected, 501 NOT_IMPLEMENTED from the controller's default branch), as predicted.
- A1 Green: controller case STEPS_TO_MITIGATE_CATASTROPHE → RequestHelpWithCatastrophe; mapper toCatastropheCommand (absent recipe → empty, absent pictures → none); test bean in RestTestConfiguration. File and fast suite green, as predicted.
- A2 green on activation, as predicted (A1's mapper already maps absence). Trials: recipe without the absence check → "expected null but was RecipeId[value=null]"; raw pictures → 500 from the domain's NPE; both as predicted, restored (cmp). Fast suite green.
- A3 green on activation, as predicted (spec if/then of STEPS_TO_MITIGATE_CATASTROPHE forbids ingredient). Test sends only `ingredient`, so the trial cannot be masked by ingredientName. Trial: branch `required: [ingredient]` removed in the classpath spec copy → "Status expected:<400> but was:<201>", as predicted; restored (cmp with the source spec). Fast suite green.
- A4 green on activation, as predicted. Trial `maxItems: 11` in the classpath spec copy → MISMATCH: predicted 422 TOO_MANY_PICTURES, the test stayed green (400 VALIDATION_FAILED, seen with a temporary print, restored). Cause: the limit is enforced twice — by the runtime spec validator and by the generated DTO's `@Size(max = 10)` (useBeanValidation, generated from the source spec at build time, so the classpath copy does not reach it). A3's if/then has no bean-validation counterpart, so its trial was complete. Reported to the user.
- A4 full trial (user's choice): source spec `maxItems: 11`, regenerated → "Status expected:<400> but was:<422>" (domain TooManyPictures, INV-38), as predicted. Spec restored (git diff empty), regenerated: `@Size(max = 10)` back, classpath copy = source (cmp). Fast suite green.
- A5 green on activation, as predicted (pattern ^https:// checked only by the runtime validator; no @Pattern in the generated DTO). Trial `^https?://` in the classpath copy → "Status expected:<400> but was:<422>" (PictureLinkNotSecure), as predicted; restored (cmp). Fast suite green.
- A6 green on activation, as predicted (else branch: no CHEF outside MENU_PROPOSAL). Trial: else's `preferredProvider not contains CHEF` removed in the classpath copy → "Status expected:<400> but was:<422>" (ChefNotAllowed, INV-01), as predicted; restored (cmp). Fast suite green.
- A7 green on activation, as predicted (use case's require(Role.COOK), error map 403). Trial: require line removed → "Status expected:<403> but was:<201>", as predicted; restored (git diff empty). HelpRequestsControllerTest fully active. Fast suite green.
- B1 Red (201 expected, 501 NOT_IMPLEMENTED from HelpMapper.toAnswer's default branch), as predicted; only B1 fails.
- B1 Green: HelpMapper.toAnswer case CatastropheMitigation (recipe, explanation, images mapped as given; absence left to B2). HelpsControllerTest and fast suite green, as predicted.
- B2 Red ("Status expected:<201> but was:<500>"; NPE "pictures is null" in ensurePicturesMayBeAttached), as predicted; only B2 fails.
- B2 Green: mitigation case maps an absent recipe to none (INV-37) and absent images to none. HelpsControllerTest and fast suite green, as predicted.
- B3 green on activation, as predicted (domain ensureMitigationMatchesRequest, INV-37 → 422 with rule). Trial: CatastropheMitigation.isAbout returns true → "Response status expected:<422> but was:<201>", as predicted (first run's grep missed the message; rerun shown); restored (git diff empty). Fast suite green.
- B4 green on activation, as predicted (Pictures maxItems 10, enforced twice as in A4). Full trial: source spec `maxItems: 11`, regenerated (`@Size(max = 11)` seen) → "Status expected:<400> but was:<422>" (TOO_MANY_PICTURES, INV-38), as predicted. Spec restored (git diff empty), regenerated: `@Size(max = 10)`, classpath copy = source. HelpsControllerTest fully active. Fast suite green.
- C1 Red ("expected: ACK but was: DEAD_LETTER"; log "dead-letter queue: AnswerKindNotBuilt"), as predicted; only C1 fails.
- C1 Green: GrandmaAnswerMapper.toAnswer case CatastropheMitigation (recipe, explanation, images via pictureLink, as given; absence left to C2). Consumer test and fast suite green, as predicted.
- C2 Red ("expected: ACK but was: RETRY"; NPE on images().stream()), as predicted; only C2 fails.
- C2 Green: mitigation case maps an absent recipe to none (INV-37) and absent images to none. Consumer test and fast suite green, as predicted.
- C3 green on activation, as predicted (domain AnswerTooLate after Grandma's side timed out at 18:45; consumer ignores it, Q-A2). Trial: AnswerTooLate removed from the ignored catch → "expected: ACK but was: DEAD_LETTER" (log "dead-letter queue: AnswerTooLate"), as predicted; restored (git diff empty). Consumer test fully active; fast suite green, 0 skipped.
- W7 green on activation, as predicted (failsafe XML: 7 test cases, 0 failures; the .txt summary says "Tests run: 0" for the outer class because of the @Nested class — a report quirk, not a skipped run). Trial: REST mapper passes no pictures → 201, then "expected: <both links> but was: \"\"" on the help_request row, as predicted; restored (cmp), IT green again. All 15 step 9 items active.

## Refactoring
- Refactor agent (isolated): 2 changes retained, 0 undone. (1) GrandmaAnswerMapper: images → pictures as one helper `pictures(@Nullable List<String>)`, toAnswer Javadoc names Catastrophe Mitigations. (2) HelpMapper: the same helper `pictures(@Nullable List<URI>)`, Javadoc updated. Domain boundary: no decision in the adapters. Proposals: P1 CatastropheMitigation recipe as Optional<RecipeId> (domain owner, steps 6/7/11); P2 toContractAnswer placeholder stays for step 11.
- Verified by me: src/test identical to the snapshot (diff -r); src/main differs from the snapshot only in the two files; fast suite green, 0 skipped. Awaiting the user's keep/undo.
- User 2026-11-16: refactoring 1 kept, refactoring 2 kept; P1 (CatastropheMitigation recipe as Optional<RecipeId>) to the planned list (later increment, step 6, domain owner's decision).

## Finish
- REST coverage gate trial: made-up operation `madeUpOperation` (tag Help Requests) in the source spec → compile error "HelpRequestsController is not abstract and does not override abstract method madeUpOperation()", as predicted; spec restored (git diff empty), regenerated (no madeUpOperation left).
- Leak grep in domain and application: none. `if`s in adapter/in: step 9 added none; the existing ones map errors, check the contract or the token (unchanged since inc 2).
- clean verify ×2 green (fast 578, slow 201 + 14 + 69, 0 skipped, BUILD SUCCESS both); generated sources identical after a clean regeneration (diff -r against the snapshot).
- Handed on: P1 to the planned list; HelpMapper.toContractAnswer placeholder for CatastropheMitigation → step 11 (read-back).
- Step 9 approved by the user 2026-11-16 (approve and commit).
