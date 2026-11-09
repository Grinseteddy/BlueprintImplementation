# Journal — increment 2, step 8 (Application services)

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02.
EXACT Coding at 88fb3f9, autonomy full-hitl. Fast loop: the use-case tests with the contract-checked fakes; wiring in `ApplicationWiringIT`.

## Test list
- Approved 2026-11-09: 18 items (`RequestHelpWithIngredientTest` 9, `ProvideHelpTest` 4, `ProvideGrandmaAvatarHelpTest` 2, `ApplicationWiringIT` 1, step 6 r1 `HelpRequestIngredientTest` 2).
- Retries, not-found and redelivery do not depend on the kind of help; accept, reject, cancel and time-out add no behaviour for ingredient requests here.
- Decided by the user: a third `provideHelp` taking a `HelpAnswer` in the domain, instead of a switch over the kinds in both use cases.

## Cycles
- Step 6 r1: `provideHelp(HelpAnswer)` switches over the sealed type; scaffold, Red, Green; the explanation case green on activation with its trial.
- `RequestHelpWithIngredient` follows the preparation-step use case; scaffold, Red, Green, then staged activations, each with a trial.
- `LenaAsCook` in the new test file is premium, so the free-plan test (R1.E3) tells the two apart.
- `ProvideHelp.Command` and `GrandmaAnswer` hold a `HelpAnswer`; six tests green on activation, four trials (save and pass-through, each only for a Substitutes answer).
- Wiring: the bean fetched inside the test, so the missing bean fails only that test; Red `NoSuchBeanDefinitionException`, one `@Bean`.
- No prediction mismatches and no process slips in this step.

## Refactoring (isolated agent, `src/test` unchanged by `diff -r`, kept by the user)
- Retained: three trace comments. Undone: `GiveHelpToRequest` (passed every test, isolates no rule; the same sequence is in the other use cases by design). The two request-help use cases: similar shape, no shared knowledge.
- `if`s in the use cases in scope: authorization (ADR0013) and redelivery (ADR0015); no business decision.

## Document correction
- ADR0013 addition (user): "the caller's provider type must be asked" is enforced in the aggregate (`ProviderNotAsked`, INV-01).

## Finish
- `verify` green (427 + 167 fast; 14 + 42 slow; Spotless, Error Prone, Spectral). Application and domain import checks: no hits.

## Handed on
- Step 9: REST mapping for ingredient requests and substitutes; the Grandma consumer and mapper; what a repeated rejected substitute from Grandma does (today it would dead-letter, as other refused answers do); blank names at the door.
