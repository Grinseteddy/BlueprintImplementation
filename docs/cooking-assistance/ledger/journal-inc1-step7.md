# Journal — increment 1, step 7 (Persistence), unit Help Request

Board B3, US-CA-03, slices B3-S03 to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Database: Testcontainers PostgreSQL 16 (Docker reachable). Fast loop: `OutboxEventMapperTest`,
`InMemoryHelpRequestRepositoryTest`; slow: `HelpRequestRepositoryIT`,
`JdbcHelpRequestRepositoryContractIT`.

## Slice check
- Each new event → its AsyncAPI message; every payload field has a source in the event. Optional
  fields: `note` (Cancelled), `helpProvider` (Accepted), `suggestedProviders` (TimedOut — written
  as `[]` when empty, R8.E3). The side state (ASKED / ANSWERED / TIMED_OUT) stays internal.
- Schema: `help.rejected` missing → new migration V2 (V1 applied, unchanged).
- The fake kept the rejection mark (it stores `Help` objects), the PostgreSQL adapter did not →
  contract item C1 makes the difference visible.
- Planned "removed Help child rows": not applicable — the domain never removes a Help (INV-39
  marks it). No uniqueness rule → no unique constraint.

## Test list (approved 2026-11-03)
17 items: M1–M7 (outbox mapping), P1–P9 (repository IT), C1 (shared contract).
Check before approval: build green, the new items skipped, existing tests green — as predicted.

## Outbox mapping (M1–M7)
- M1, M3, M5, M6: Red as predicted (`UnsupportedOperationException` from the placeholder); Green
  one mapping method per event; M6 removed the last placeholder.
- M2 (no note), M4 (Grandma, no helpProvider), M7 (`[]`): green on activation as predicted
  (`NON_NULL` writer; an empty list is not null); trials (`orElse("")`, a stand-in provider, empty
  list → null) each failed exactly that item.
- Refactor (isolated agent, tests unchanged, user: keep): `askedProviders(...)` helper (3 uses),
  `providerType(...)` helper (5 uses), methods in switch order. No business rule in the mapper.
- Formatting slip: one test line too long (M7), fixed with `spotless:apply` (layout only, user
  approved). From then on `spotless:apply` after every test edit.

## Persistence (P1–P9, C1)
- P1: Red as predicted (column query empty); Green `V2__help_rejected.sql`
  (`rejected boolean not null default false`).
- C1: fake green on activation, PostgreSQL Red at `isRejected()` as predicted; Green `HelpRow`
  gains `rejected`, the mapper writes it and reads it through the 6-argument `reconstitute`.
  Trial: the fake stores Helps without the mark → C1 fails against the fake. As predicted.
- P2 (CANCELLED), P3 (CLOSED, accepted), P5 (TIMED_OUT side): green on activation; trials
  (CANCELLED written as OPEN, accepted written false, TIMED_OUT written ASKED) each failed exactly
  that item.
- P4 (rejected, answered again, accepted; two rows): green on activation; trial (rejected read as
  false) failed P4 and C1 against PostgreSQL — as predicted.
- P6–P9 (outbox rows): green on activation; trials (misspelled event type, helpId = request id,
  empty note, swapped provider lists) each failed exactly that item.
- Refactor (isolated agent): no change retained or needed; boundary candidate (own Help mapper)
  rejected — Helps are entities of the aggregate, one reason to change.

## Unit checkpoint
- `mvn -o clean verify`: green — cooking-assistance 210 + 66, larder-app 9 + 29 (12 skipped: the
  increment 1 acceptance scenarios until step 12).
- Leak checks: no persistence framework in the domain; no adapter import in domain, application
  or fakes. Migrations: V1 unchanged, V2 new.

## Summary
- Test items 17/17 active; behavioral prediction mismatches 0; process slips 1 (formatting of a
  test line, M7); trials 14; refactor groups 2 (mapper kept, persistence no change).
- Open: `Help.reconstitute` 5-argument form no longer used in main code (only a domain test) →
  candidate for removal in the domain; the read side reads no Helps yet — when step 11 shows
  Helps it needs `accepted` and `rejected`.
