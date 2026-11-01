# Journal — increment 0, step 13 (Independent review)

## Reverse check (separate agent, Sonnet, read-only)
- Scope: US-CA-03, B3-S01 + B3-S02; columns from V1 applied to a throw-away PostgreSQL 16 container.
- 19 findings; matches: names 41, columns 12, enum values and codes 24, states 9, examples 25/25.
- Decisions 2026-11-01: drifts 1–6, 9, 13, 14, 17 corrected in the documents (step 3 r2 invariants incl. the R1.E4 and R2.E3 cards reworded; step 6 r1 design brief; ADR0008 addition; ADR0010 409 CONCURRENT_UPDATE; boards-slices.md §5); drift 10 (NoHelpProviderChosen 422 with a 400 code) stays on the planned list; 8, 11 unchanged; 7, 12, 18, 19 no action. Commit c59e571.

## Fagan inspection (one inspector, Sonnet, architecture focus, read-only)
- Package: code, tests, build and generator configuration, brief, slice plan, invariants and Example Mapping, specs, ADRs, principles (larder-adr.ttl), quality scenarios, step 12 results, planned list. Not given: ledger, journals, conversation.
- Result: 1 major, 7 minor, 5 questions.
- Decisions: D-01 (major, consumer not a tolerant reader) accepted, rework now; D-02 (Problem without code for unmapped Spring errors) and D-06 (torn read in byId) accepted, fixed now; D-03, D-04, D-05, D-07 deferred (planned list, inc 1); D-08 (message ID v3) rejected — stable ID from the outbox row (step 10, ADR0015); Q-01, Q-02, Q-05 → step 14; Q-03 answered by ADR0010; Q-04 planned (traceparent, inc 1).

## Rework cycles
- D-01 (step 9): Red `unknownFieldsAreIgnored` expected ACK but was RETRY as predicted. **Mismatch in the cause:** I predicted the payload's unknown field to throw first; the nested answer's did — Jackson collects unknown properties of a creator-built record and reports them after building it, while the nested answer is built during the parse. Both levels failed, the test covers both. Green: FAIL_ON_UNKNOWN_PROPERTIES off. Refactor (agent): AsyncApiMessageReader extracted — kept. Full reactor verify green. Commit 64920d1.
- Found on the way: the spec asks to ignore unknown enum values, the closed schema enums reject them → planned list (step 4, inc 1).
- D-02 (step 9): Red 406 test "No value at JSON path $.code" as predicted. **Mismatch at Green:** the override checked `body instanceof ProblemDetail`, but Spring passes a null body to handleExceptionInternal for most of its errors (confirmed in spring-webmvc bytecode); corrected Green: let the parent build the response, then add the code. Step 4 r6: OpenAPI 1.0.5 names NOT_ACCEPTABLE. Refactor (agent): Problems.carriesCode, named unnamedError entry — kept.
- D-06 (step 11): test in a new JdbcHelpRequestQueriesIT (persistence package; the adapter is package-private) forcing the race through a wrapped DataSource. Red expected OPEN but was ANSWERED as predicted; Green read-only REPEATABLE READ TransactionTemplate as predicted. Refactor (agent): oneSnapshot factory, lambda renamed — kept. Full reactor verify ×2 green (concurrency). Commit 8df6132 (D-02 + D-06).
- Intent coverage after the rework: no gaps.

## Reverse check of the rework (separate agent) and re-inspection (new inspector, Fable)
- Reverse check: 10 matches; drift #1 (framework 500 → INTERNAL_SERVER_ERROR instead of INTERNAL_ERROR) → defect, fixed test-first; #5 ADR0016 lacks the snapshot rule → ADR0016 addition (step 11 revision); #4 ledger entries (this journal).
- Re-inspection: D-01 fixed, D-06 fixed, D-02 partially fixed; new R-01 (status-name codes; framework 500s not logged) = drift #1, widened; R-02 (non-standard status throws in HttpStatus.valueOf) → planned list; Q-01 answered (the default response covers 406); Q-02 (@Validated on list GETs → 500) → planned list for inc 1.
- R-01 (step 9): T1 Red INTERNAL_SERVER_ERROR vs INTERNAL_ERROR as predicted, Green 5xx → INTERNAL_ERROR; T2 Red BAD_REQUEST vs VALIDATION_FAILED as predicted, Green 400 → VALIDATION_FAILED.
- Refactor (agent): NamedProblem enum (one line per named entry), Problems.response — kept; spotless prediction off once (line length miscounted, formatting only). Full reactor verify ×2 green.
