# Journal — increment 2, step 6 (Domain model), unit Help Request

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02, plus the B3 commands for ingredient requests.
EXACT Coding at 88fb3f9, autonomy full-hitl. Fast loop: `HelpRequestIngredientTest`, `HelpRequestPropertiesTest` (jqwik, tries 1000, seed 20261031); slow suite and gates at the unit checkpoint.

## Before the test list
- Readiness gate 2026-11-08; red cards and Q-D1, Q-D2, Q-D3 answered by the user.
- Test list approved: 52 items. Decisions: S1 accept a whole Help; S2 requirements required (OpenAPI 1.0.13); S3 same substitute = same set of ingredient names, ignoring amounts, case and outer spaces, every substitute of a rejected Help counts, a mix containing a rejected ingredient is new; S4 later requests show the note and do not refuse (step 11).
- Sealed `HelpAnswer` (`PreparationStepExplanation`, `Substitutes`) chosen by the user.

## Added on the way
- Blank name (2 items): `aSubstituteIngredientHasAName`, `everyIngredientOfAMixHasAName`. Found by the refactor agent; decided by the user 2026-11-09: refused like no substitute (INV-12, step 3 r13). "A substitute should reference a known ingredient" recorded as an open question for the increment that integrates the Recipe Catalog (L3, D6).
- Properties (4): INV-10, INV-12, INV-14, INV-17. Omitted from the approved list by mistake although the sheet asks for a property on cardinality and quantitative rules; found before the checkpoint, added with the user's go-ahead. INV-11 has none: one `ingredient` field, no count to vary.

## Cycles
- Every Red failed for the predicted behavioral reason; every item green on activation had a trial.
- Reconstitution: a second `reconstitute` taking ingredient and requirements as scaffold (compile error is no behavioral Red); Red `expected IngredientId, but was null`; the old signature delegates with null, null.
- Trials in the last group: five for INV-15, six for cancel and time-out, one for the mix, four for the properties. Each failed exactly the predicted test and was undone.

## Prediction mismatches and slips
- S2 broke `HelpRequestsControllerTest` (501 became 400); the line added to the test by the user's decision.
- The sealed type broke more approved test lines than the one predicted; three casts added by the user's decision.
- Accept group, trial A: six tests failed where five were predicted; `aClosedOrCancelled…` also depends on accept.
- Process slip: a self-contradictory prediction for `anExplanationDoesNotAnswerAnIngredientRequest`, corrected before the run.
- Refactor agent: `spotless:apply` touched one test statement it had predicted untouched, a formatting fault already in the new test; fixed by the formatter.

## Refactoring (isolated agents, `src/test` unchanged by `diff -r`, kept by the user)
- B2-S01, B2-S02: `ensureProviderMayAnswer`, `recordHelp`, `opened`, `Substitutes.proposesSomething`, trace comments. Sealed "what the request is about" not taken.
- Remaining group: `PROVIDERS_TO_SUGGEST`, traces with US-CA-02, one `answerTimeOf(type)`, `Help.rejectedSubstitutes()`, `Substitutes.repeatsAnyOf(...)`. No undone trials. A type for the per-kind fields considered and not tried: `reconstitute` must accept stored state as it is.

## Finish
- `verify` green twice (393 + 137 fast with the properties; 14 + 42 slow; Spotless, Error Prone, Spectral). Leak check: no hits.

## Handed on
- Step 7: `HelpRequestMapper` calls the overload without ingredient; columns for ingredient and requirements; `Substitutes` in `AnswerJsonMapper`.
- Step 9: `OutboxEventMapper` and the REST 501 for ingredient requests (with step 10); blank names arrive here (`minLength: 1` alone allows spaces).
- Step 11: the "later" part of INV-15 across requests (S4).

## Reworked in step 11 (step 3 r15, user 2026-11-09)
- The aggregate and `HelpRequested` carry `ingredientName`; `ensureOneIngredientOfOneRecipe` refuses a null or blank name with `IngredientNameRequired` (INV-11, `MISSING_FIELD`), after `IngredientRequired`. Test-first; see journal-inc2-step11.md.
