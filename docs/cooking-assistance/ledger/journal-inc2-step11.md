# Journal — increment 2, step 11 (Query side)

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02.
EXACT Coding at 88fb3f9, autonomy full-hitl. Read side per ADR0016 (the aggregate's own tables, one snapshot). Fast loop: the fast suite; slow: `HelpRequestQueryIT`, `HelpQueryIT` (Testcontainers PostgreSQL 16). Generators unchanged; the specs went back to step 4 once (r21).

## Test list
- Approved 2026-11-09: 16 items (`HelpRequestQueryIT` 11, `HelpQueryIT` 5). Earlier notes match on cook, recipe and ingredient; both kinds of note; a cancellation without a note carries nothing (user).
- Changed by the user's decision on R5.E3 (below): earlier notes match an ingredient of the same name in any recipe. One item renamed (user, 2026-11-11): `previousNotesLeaveOutTheSameIngredientOfAnotherRecipe` → `previousNotesLeaveOutAnotherIngredientOfTheSameRecipe`, so the method name says what its display name says.

## Returns to earlier steps (user, 2026-11-09)
- **R5.E3, option A:** notes carry over to an ingredient of the same name in any recipe; the client sends a required `ingredientName` with the request, taken as given (L3). Replaces the recipe-and-ingredient match.
- **Step 3 r15:** INV-11 (the request names its ingredient), INV-15 (notes of earlier requests about an ingredient of the same name).
- **Step 4 r21:** OpenAPI 1.0.14 (`ingredientName` required for `INGREDIENT_SUBSTITUTE`, read and create, forbidden for the other types; `previousNotes` across recipes; version 1.0.14), AsyncAPI 1.0.10 (`HelpRequestCreated.ingredientName`, optional); CHANGELOG; `contract.yml` and `schemathesis.toml` comments name 1.0.14.
- **Rework of steps 6–9, test-first (12 items approved 2026-11-09):** domain (`ingredientName` on the aggregate and `HelpRequested`), V8 `help_request.ingredient_name`, row and mapper round-trip, outbox payload, use case `Command`, REST mapping and guards (no name, empty name → 400; a step request with a name → 400), and last the domain guard `IngredientNameRequired` (INV-11, `MISSING_FIELD`) for a null or blank name, with its use case pass-through and REST 422 for a name of only spaces. No scaffold nulls left.

## Cycles
- `ingredientRequestIsReadBack`: Red as predicted (the schema's then-branch missed `ingredient`, `ingredientName`, `substituteRequirements`). Green: `HelpRequestView` carries them, `readById` selects them, the REST mapper sets them.
- `previousNotesOfTheSameIngredientAreShown`: Red as predicted (`how_to_step` NULL = NULL matched nothing). Green: the earlier-notes query also matches the ingredient name.
- `previousNotesMatchNamesWithoutCaseOrOuterSpace`: Red as predicted. Green: `lower(trim(...))` on both sides (S3).
- `helpsOfAnIngredientRequestShowTheSubstitutes`: Red as predicted (`ClassCastException` → 500). Green: `HelpView.answer` is a `HelpAnswer`, the cast is gone, `HelpMapper` switches over the sealed answer and maps `Substitutes`.
- Green on activation, each with a trial that failed as predicted: the list by type, a step request without ingredient fields, another ingredient of the same recipe, another ingredient of another recipe, a cancellation without a note (trial in `cancel`), a cancellation note shown, another cook's note, a step request leaves out ingredient notes, one Help read back, a mix in order, 0.125 kg exactly, `listHelps` by type.

## Corrections and slips
- **Faulty trial:** `aLaterRequestDoesNotRefuseTheRejectedSubstitute`'s first trial (the domain refuses every proposal) fired in the test's own arrange step — an error, not the predicted 201-vs-422 failure. Redone narrower in `ProvideHelp`, where a refusal across requests would be built: failed as predicted.
- **Wording only:** a trial failed on the predicted assertion and values (200 vs 201) with JUnit's "expected/but was" text, not AssertJ's compareTo text. Counted as matched (user).
- **Prediction slips (counts):** a suite total counted the activated tests twice (Maven's total already includes skipped tests); the full-verify prediction misread the ledger's "473 + 167 fast; 14 + 42 slow" — each half is one module, fast + slow.
- **Infrastructure:** the Docker daemon was down at one Red (no context, no assertion); restarted by the user, the Red reran as predicted.
- `spotless:apply` four times, formatting only: two test files (an unused `Disabled` import, a line wrap), a Javadoc rewrap, a signature wrap in `HelpMapper`.

## Refactoring (isolated agent, `src/test` byte-identical by sha256 of 95 files, all kept by the user 2026-11-11)
- Retained: the earlier-notes query named `NOTES_OF_EARLIER_REQUESTS_ABOUT_THE_SAME_STEP_OR_INGREDIENT`; the two rules as named fragments `ABOUT_THE_SAME_STEP_OF_THE_SAME_RECIPE` (INV-07) and `ABOUT_AN_INGREDIENT_OF_THE_SAME_NAME` (INV-15, S3) — the domain-boundary trial, compiled SQL identical; an absent step or ingredient is `null`, not an id holding null; `HelpMapper`'s answer mappers named `toContract…`. Undone: none.

## Finish
- `verify` green (cooking-assistance 487 fast + 188 slow; larder-app 14 fast + 42 slow; Spotless, Error Prone, Spectral). No generated or framework types below the adapters.

## Handed on (planned list)
- PostgreSQL `trim` strips spaces only, the domain's `strip()` all whitespace: names differing by tabs or non-breaking spaces at the ends match in the guard but not in `previousNotes`. A decision for the user, test-first.
- `readById` builds `RecipeId` unconditionally: right for both current types, wrong for a type without a recipe (catastrophe help).
- Step 12: acceptance scenarios and intent coverage for US-CA-02 (inventory-inc2).
