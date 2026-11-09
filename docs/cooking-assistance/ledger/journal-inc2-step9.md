# Journal — increment 2, step 9 (Inbound adapters)

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02.
EXACT Coding at 88fb3f9, autonomy full-hitl. Generators unchanged (openapi-generator, Modelina). Fast loop: `@WebMvcTest` with the contract-checked fakes, the consumer called with raw messages.

## Test list
- Approved 2026-11-09: 25 items (`HelpRequestsControllerTest` 7, `HelpsControllerTest` 11, `ProblemDetailsAdviceTest` 1, `GrandmaHelpProvidedConsumerTest` 6).
- Decided by the user: a repeated rejected substitute from Grandma is ignored (ACK, one INFO line); the three placeholder tests (two 501, one dead letter) replaced by the first test of each group, deleted at its Green.

## Cycles
- `createHelpRequest` routes `INGREDIENT_SUBSTITUTE`; `HelpMapper` and `GrandmaAnswerMapper` map substitutes; amounts stay `BigDecimal` over REST, from `double` with `BigDecimal.valueOf` on the Grandma path.
- Blank name: 422 `VALIDATION_FAILED`, rule INV-12 over REST; dead letter from Grandma.
- Schema tests had trials on a temporary copy of the rule in the spec, restored after each.

## Corrections and slips
- List item corrected before activation: a Chef on an ingredient request is 400 `VALIDATION_FAILED` (schema else branch), not the 422 I listed; the preparation-step test already pins 400. Its trial fails both Chef tests.
- `substitutesAgainstTheSchemaAreDeadLettered` also asserts `InvalidMessage` in the log, or its trial could not fail: the domain would dead-letter an empty list too.
- Otherwise every Red failed for the predicted reason and every trial failed exactly the predicted tests.

## Refactoring (isolated agent, `src/test` unchanged by `diff -r`, kept by the user)
- Retained: `toPreparationStepCommand`, `preferredProviders(body)`, `ignored(...)`. Undone: one catch with a pattern switch in the consumer. REST and Grandma substitute mappings not merged: two contracts, different amount types.

## Document corrections (user)
- AsyncAPI 1.0.9 (step 4 r20), ADR0015 addition, INV-15 (step 3 r14): the repeated rejected substitute from Grandma is ignored.

## Finish
- `verify` green (473 + 167 fast; 14 + 42 slow; Spotless, Error Prone, Spectral). No generated or framework types below the adapters.

## Handed on
- Step 11: the `PreparationStepExplanation` cast and `HelpView` for listing a Substitutes Help; `HelpRequestView` without ingredient and requirements; the "later" part of INV-15 (S4).
