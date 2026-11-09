# Journal — increment 2, step 7 (Persistence), unit Help Request

Board B2 "Help with ingredient substitution", US-CA-02, slices B2-S01 and B2-S02, plus the B3 commands on stored ingredient requests.
EXACT Coding at 88fb3f9, autonomy full-hitl. Database: PostgreSQL 16 on Testcontainers. Fast loop: `OutboxEventMapperTest`, the repository contract on the fake; unit IT `HelpRequestIngredientRepositoryIT`.

## Test list
- Approved 2026-11-09: 21 items (`HelpRequestIngredientRepositoryIT` 16, `OutboxEventMapperTest` 4, `HelpRequestRepositoryContract` 1). I first announced 22 and 17; corrected at the first Red.
- Locking, failed saves and orphan rows do not depend on the kind of help and stay in `HelpRequestRepositoryIT`.

## Cycles
- `V7` adds nullable `ingredient` and `substitute_requirements`, no foreign key (D6, L3).
- `OutboxEventMapper`: `howToStep` only when there is one, `ingredient` and `substituteRequirements`; `Substitutes` to the generated message model (`value` as `double`).
- `HelpRequestRow`, `HelpRequestMapper` through the new `reconstitute`; `AnswerJsonMapper` stores and reads both kinds, with the amount as an exact decimal (`readTree` only for `answerType`, so the amount never passes through a double).
- In-memory fake carries the two fields (contract Red on the fake only).
- Order changed twice before a run: the outbox-mapper items went first, because `save` writes the outbox row and the round-trip would have died there.
- Every Red failed for the predicted reason; every item green on activation had a trial.

## Prediction mismatches and slips
- "No substitutes published" trial: predicted two failing tests; `everyUnitIsAnAsyncApiUnit` and `anAmountIsWrittenAsTheSameNumber` failed too, as they read the first published substitute. The trial broke more than one behaviour; no defect.
- Process slip: the first activation script stopped on a one-line `@Disabled`, so tests were not activated and that run's trials were worthless; discarded and redone, the script guarded to stop on failure.

## Refactoring (isolated agent, `src/test` unchanged by `diff -r`, kept by the user)
- `AnswerJsonMapper`: named constant, `answerTypeOf`, one `toPayload` per level.
- New `AnswerMessageMapper`, moved out of `OutboxEventMapper`: a new kind of answer changes independently of a new event. Stored and published answers not merged: the stored amount must stay exact. No undone trials.

## Finish
- `verify` green (410 + 166 fast; 14 + 42 slow; Spotless, Error Prone, Spectral). Leak checks: no hits. Applied migrations unchanged, `V7` new.

## Handed on
- Planned: `AnswerJsonMapper.toAnswer` becomes exhaustive with the next kinds of answer (a missing `answerType` is a NullPointerException, unreachable today); `recipe` in `HelpRequestCreated` for B4 (INV-19, optional recipe).
- Accepted (user): the published amount is a `double`; an amount too long for a double would differ from the stored one.
- Step 11: `JdbcHelpRequestQueries` still casts to `PreparationStepExplanation`.
