# Independent review — Cooking Assistance increment 0 (walking skeleton)

Step 13, US-CA-03, slices B3-S01 + B3-S02. Moderator: the orchestrating session. Reviewers were separate agents in fresh contexts; none was given the ledger, the journals or the conversation.

## 1. Reverse check (documents against code)
- Agent: Sonnet, read-only. Columns read from V1 applied to a throw-away PostgreSQL 16.
- Matches: names 41, columns 12, enum values and codes 24, states 9, examples 25/25.
- Drifts 19. Corrected in the documents: 1–6, 9, 13, 14, 15, 16, 17 (step 3 r2, step 6 r1, ADR0008 addition, ADR0010, boards-slices.md). Planned: 10 (NoHelpProviderChosen, 422 with a 400 code). Unchanged: 8, 11. No action: 7, 12, 18, 19. Commit c59e571.

## 2. Fagan inspection
- One inspector, Sonnet, architecture focus, read-only (≈ 75 of 138 files, ≈ 7,000 lines, 25 min).

| # | Sev | Defect | Decision |
|---|---|---|---|
| D-01 | major | Grandma consumer not a tolerant reader: an unknown field → retried and dead-lettered | reworked test-first (64920d1) |
| D-02 | minor | inherited Spring errors answer a Problem without `code` | reworked (8df6132), OpenAPI 1.0.5 |
| D-03 | minor | API error codes in domain rejections; status by string switch | deferred, increment 1 (with drift 10) |
| D-04 | minor | relay and persistence adapter share the outbox SQL; no ArchUnit rule between adapters | deferred, increment 1 |
| D-05 | minor | claim query without batch bound holds locks during broker confirms | deferred, increment 1 (relay work) |
| D-06 | minor | `byId` reads two tables in separate transactions | reworked (8df6132), ADR0016 addition |
| D-07 | minor | operations without a scope rule fall to `authenticated()` | deferred; resolved with SEC-02 in step 14 |
| D-08 | minor | message ID is a v3 UUID, not v7 | rejected: stable ID from the outbox row (step 10, ADR0015) |

Questions: Q-01, Q-02, Q-05 → step 14 (answered there as SEC-04, SEC-08, SEC-10); Q-03 answered by ADR0010 (redelivery on the consumer path); Q-04 planned (traceparent, log lines, increment 1).

## 3. Rework
- Each defect: Red for the predicted reason, Green, Refactor by an isolated agent (kept), full reactor verify. Prediction mismatches 2 (D-01: the nested field failed first; D-02: Spring passes a null body), journaled.
- Intent coverage after the rework: no gaps.

## 4. Reverse check of the rework and re-inspection
- Reverse check (Sonnet): 10 matches; drift #1 (framework 500 → INTERNAL_SERVER_ERROR) → defect; #5 ADR0016 → addition; #4 ledger.
- Re-inspection (new inspector, Fable, 24/24 rework files): D-01 fixed, D-06 fixed, D-02 partially fixed. New: R-01 minor (status-name codes, = drift #1) → reworked test-first (e4a8003, NamedProblem); R-02 minor (non-standard status in the fallback) → planned list. Q-01 answered (the `default` response covers 406); Q-02 (@Validated on list GETs) → increment 1.

## 5. Result
No open major defect. Step 13 approved 2026-11-01.
