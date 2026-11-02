# Journal — increment 0, step 11 (Query side)

## Slice check (decisions 2026-11-01)
- One GET in the skeleton: getHelpRequestById. ADR0016 followed (aggregate tables, query port + JDBC adapter, no projection) — deviation from the read-model skill's "never query the write tables" recorded; projection items (gaps, rebuild, runner lock) do not apply this increment.
- Read access: any caller with help:read. preferredProvider derived from the asked providers (increment 1 stores it).
- Left out (optional): notes, previousNotes, suggestedProviders, acceptedHelpLink (inc 1), helpsLink (until listHelpsOfHelpRequest), createdAt/updatedAt (not stored).

## Test list
- HelpRequestQueryIT 6 items; verify green with 6 skipped as predicted.

## Cycles
- Q1: Red 501 (route) as predicted. Before Green: generated HelpRequest initialised optional lists and wrote nulls → generator options containerDefaultToNull + generateJsonIncludeAnnotations (approved; 106 fields get NON_NULL); A17 removed and a stand-in query port added to the REST slice tests (approved). Green: query port, view, JDBC adapter, mapper (orElseThrow minimal). Refactor: import order.
- Q2: green; trial state always ASKED → only Q2 fails, as predicted.
- Q3: Red 500 (NoSuchElementException) → HelpRequestNotFound → 404.
- Q4: Red no $.code (Spring's type-mismatch 400) → handleTypeMismatch → VALIDATION_FAILED.
- Q5: Red 404 (no GET scope rule) → GET → SCOPE_help:read.
- Q6: green; trial adapter always empty → Q1, Q2, Q6 fail with 404, as predicted (no lag to show without a projection).
- Finish: clean verify green (114 fast + 46 slow); compile coverage gate green; leaks none; no repository/reconstitute on the query path; no if on the query side. 0 prediction mismatches, 0 process slips.
- Slice refactoring (one agent): kept preferredProviders() in the view and the named read; tests unchanged; clean verify green. Step 11 approved 2026-11-01.
