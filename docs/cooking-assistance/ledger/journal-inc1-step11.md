# Journal — increment 1, step 11 (Read models), the help request and Help reads

Board B3, US-CA-03, slices B3-S03 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Read side per ADR0016: the query adapter reads the aggregate's own tables in one read-only
REPEATABLE READ snapshot (read-your-writes, QS-07); no projector, no projection tables. Fast loop:
the fast suite; slow: `HelpRequestQueryIT`, `HelpQueryIT` (new), `JdbcHelpRequestQueriesIT`
(Testcontainers PostgreSQL 16). Generator unchanged; the spec went back to step 4 once (r11).

## Slice check (user 2026-11-04)
- Read models: Help request (getHelpRequestById, listHelpRequests) and Help
  (listHelpsOfHelpRequest, getHelpOfHelpRequest, listHelps).
- F1: the notes existed only in events — no field could fill `notes`, `previousNotes`,
  `rejectionNote`. F2: the spec stated no list order. F3: who may list. F4: chef helps.
- Later question L6, answered by the user and recorded as the ADR0016 addition: notes live with
  the aggregate; keyset paging oldest first by UUIDv7 id, `limit + 1`, opaque base64url cursor, a
  cursor the API did not issue answers 400 VALIDATION_FAILED; any `help:read` caller may list; chef
  helps hidden from increment 4 on (planned).
- F5 (user, 2026-11-04): a rejection note names the Help it rejects, so `Help.rejectionNote` can be
  filled.

## Returns to earlier steps (from this slice check)
- **Step 4 r11:** OpenAPI 1.0.8 — the three list operations state the order ("oldest first … a later
  page never repeats an item"), the cursor says "A cursor this API did not issue answers 400", the
  version enum and default 1.0.8; CHANGELOG entry. User approved. ADR0009's Consequences table name
  corrected (`note` → `help_request_note`, user's choice).
- **Step 6 r1 (notes in the domain, D1–D12):** `Note(kind, text, writtenAt, help)`, `NoteKind`;
  `cancel` and `reject` write notes after their guards, one instant per command; reconstitution
  restores them (D8). F5: a rejection note names its Help (D9; approved expected values of D4, D5,
  D7, K7, P12 updated with `HELP_ID`). Paused 2026-11-04, resumed 2026-11-05: D10/D11 — the compact
  constructor refuses a REJECTION note without a Help and a CANCELLATION note naming one (D8's input
  changed to name `HELP_ID`, approved). D12 came from step 11 (Q6, below).
  Process slip: D4's Green was checked without a prediction first. D6's first trial edit duplicated
  the write instead of moving it (D2, D5, D6 failed — a faulty trial, not a result); redone as a
  move, only D6 failed.
- **Step 7 r1 (notes in persistence, K6–K8, P11–P15):** V3 `help_request_note` (child rows of the
  aggregate, `@MappedCollection`, rewritten on every save like `asked_provider` and `help` — the
  list had said append-only; corrected at the Green), V4 `help_id`, V5 a foreign key
  `help_id → help(id)` **deferrable initially deferred**, because Spring Data JDBC deletes and
  re-inserts the child rows on every save.
  - **P14 — behavioral prediction mismatch:** V4 broke P11's exact column list; user: P11 checks what
    V3 made (`startsWith`).
  - **P15:** before activation, the second foreign key would break the approved P11 (exactly one FK)
    and P14 ("no foreign key"); user: P11 checks the FK to `help_request`, P14 the column only.
  - **P15 Green — behavioral prediction mismatch:** 26 of 28 IT errors in `@BeforeEach`; the 11 test
    resets deleted `help` before `help_request_note`, and autocommit checks the deferred FK at once.
    User: reorder the resets (notes before helps). 28/28 green.
  - The FK checks existence only; enforcing "a Help of the same request" in the schema was declined
    by the user (the aggregate enforces it).
- Refactors (isolated agents, tests unchanged): step 6 r1 none; F5 domain factories
  `Note.rejection`/`cancellation` and NoteRow Javadoc kept; D10/D11 INV labels moved onto the
  constructor guards; V5 comment corrected ("an existing Help"). Two F5 agents ran in parallel by
  mistake and collided in `tools/codegen` (`npm ci`); both re-ran — agents that build now run one
  after another.

## Test list (approved 2026-11-05)
24 items: HelpRequestQueryIT Q1–Q7, L1–L8; HelpQueryIT H1–H7, G3; JdbcHelpRequestQueriesIT S1;
gates G1 (the 501 test loses its rows), G2 (leak and query-path checks). Added on the way: Q4b
(user), D12 (user, from Q6). Dropped: L8 (user) — L5 already drives Q-02; Q-02 added to L5's trace.

## getHelpRequestById (Q1–Q7, Q4b)
- Q1 Red (`notes` null) / Green: the view reads the notes in the snapshot; the mapper sets `notes`
  only when there are any (the spec's optional arrays are absent, never empty).
- Q2 green on activation; trial (kind mapped to a constant CANCELLATION) failed only Q2.
- Q3 Red / Green `PreviousNote` with `helpRequestLink`; Q4 Red / Green same requester, recipe and
  step (INV-07, red card Q1).
- Q4b (user): "earlier" = creation order. Q3's setup changed to UUIDv7 ids in creation order
  (approved; the fixture `HELP_REQUEST_ID` is a v4 UUID and sorted after the next month's id).
  Red / Green `earlier.id < read.id`.
- Q5: the approved first test updated to expect `helpsLink` (approved). Red both as predicted /
  Green `helpsLink` always, `acceptedHelpLink` once a Help is accepted.
  **Behavioral prediction mismatch:** the `acceptedHelpLink` assertion read `[]` — AssertJ treats a
  `JsonNode` as an Iterable, so `extracting` mapped its (no) elements; a test defect, the link was
  there. User: fix the assertion; trial (no `acceptedHelpLink`) failed only Q5.
- Q6 Red. Design question: `suggestedProviders` was computed only inside `timeOut`. User: a pure
  domain function `HelpRequest.suggestionsAfterTimeOut` (suggestions only once every asked provider
  timed out) with a new domain test D12 first. D12 Red / Green; Q6 Green through
  `HelpRequestView.suggestedProviders()`.
- Q7 green on activation; trial (`notes` always set) failed Q7 and the first test.

## listHelpRequests (L1–L7)
- L1 Red (501) / Green `list()`; G1 row for `GET /help-requests` removed; the query-port stand-in in
  `RestTestConfiguration` became an anonymous class.
- L2 Red / Green `HelpRequestFilter`, `cast(:x as type) is null or …`.
- L3 Red / Green `Page<T>(items, hasMore)`, `limit + 1` keyset, `Cursors` base64url.
- L4 green on activation; trial (`id >= :after`) failed L3 and L4.
- L5 Red as predicted (uncertain prediction stated): `limit=0` → `ConstraintViolationException`
  from the `@Validated` generated interface → 500. Green: mapped to 400 VALIDATION_FAILED (Q-02).
- L6 Red (`abc` → `IllegalArgumentException` → 500) / Green `InvalidCursor` → 400.
- L7 green on activation; trial (`GET /help-requests` login only) failed only L7.

## Help reads (H1–H7, G3, S1)
UUIDv7 ids for the Helps in the tests, as for Q3.
- H1 Red (501) / Green `HelpView`, `helpsOf` through `HelpRow` and the write side's
  `HelpRequestMapper.toDomain(HelpRow)`, the answer back into the generated model; G1 row out.
- H2 green on activation; trial (`isAccepted` false) failed only H2.
- H3 Red / Green: left join on the rejection note naming the Help → `rejectionNote`.
- H4 Red (200 for an unknown request) / Green `Optional<Page<HelpView>>`, exists check, keyset;
  `Cursors` works on `UUID`.
- H5 Red / Green `helpOf`, `HelpNotFound` → 404 NOT_FOUND (a Help of another request is not found).
- H6 Red / Green `HelpFilter`, `helps(...)`, the type filter on the request's type (INV-37: an answer
  has the type of its request); G1 complete — the parameterized 501 test had no rows left and was
  deleted.
- H7 green on activation; trial (`GET /helps` login only) failed only H7.
- G3 (wiring: reject over HTTP, then read) green on activation; trial (controller without
  `rejectHelp.execute`) failed only G3.
- S1 (a cancel commits after the notes read) green on activation; trial READ COMMITTED failed both
  snapshot tests (ANSWERED, CANCELLED — torn views).
- Process slip: one build command was stored in a zsh variable and did not run; no result was
  claimed, it was rerun with the command written out.

## Refactors (isolated agents, one after another, tests unchanged by `diff -r`; all kept by the user)
- Domain: `timeOut` uses `suggestionsAfterTimeOut`; `providersNotYetAsked` removed (INV-09 has one
  owner). Spotted INV-01 written twice (`ensureProvidersMayBeAsked` vs `PREPARATION_STEP_PROVIDERS`)
  → planned list.
- Query adapter (help request): snapshot and INV-07 Javadoc; note and asked-provider rows through the
  write side's `HelpRequestMapper.toDomain` (one row-to-value conversion).
- REST (help request): one link template, `absentWhenEmpty`, `toPreviousNote`.
- List adapter: `idsAfter` extracted. REST list: ADR0019 Javadoc back on the 413 handler (my
  insertion slip); `toFilter`/`toPage` in `HelpRequestMapper`.
- Help query adapter: port Javadoc on the right method (my slip); `keysetPage(limit, fetch)` —
  `limit + 1` once, in the adapter (ADR0016 L6); `helpsAfter` reads.
- Help REST: `Cursors.next`/`after`, `OptionalArrays`, `HelpVocabulary` (the shared HelpType and
  HelpProviderType conversions), `// op:` comments.

## Finish
- Leak check (domain, application: generated, web, jdbc, data, http, jakarta.validation): none.
  No repository or aggregate root on the query path. Every GET operation is served; 501 remains only
  for planned write variants (other answer kinds and request types, `nextChef`).
- Read model → question → source → operations:
  - Help request (a Cook, a provider: what is asked, what happened, what didn't work before):
    `help_request`, `asked_provider`, `help`, `help_request_note` → getHelpRequestById,
    listHelpRequests.
  - Help (the requester: which answers, which accepted or rejected and why; a provider: my answers):
    `help`, `help_request`, `help_request_note` → listHelpsOfHelpRequest, getHelpOfHelpRequest,
    listHelps.
- Consistency: same transaction as the write (read-your-writes), shown by G3 and the step 9 wiring
  test; no lag to show. Rebuild: not applicable (no projection tables).
- Open risks: N+1 reads per list page (at most 100 views, ADR0016 accepts it); listing another
  Cook's requests (privacy) is a question for the security review (ADR0016 L6); notes are rewritten
  with every save (Spring Data JDBC child rows).
- Planned list: chef helps hidden from increment 4 (ADR0016 L6); INV-01 duplication; acceptance
  scenarios of increment 1 (step 12).
