ADR0016

# Queries read the aggregate tables

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will answer queries from the aggregate's own tables in the command database; separate projections are added only when a read model needs another shape.**

## Context
QS-07 requires read-your-writes. The skeleton has one GET.

## Options considered

### Read aggregate tables

read-your-writes by construction

### Separate projections from day one

eventual consistency without a need

## Consequences

- Increment 1 revisits this for `previousNotes` and the provider lists (L6)

## Principles
—

## Quality scenarios
QS-07, QS-01

## Addition (step 11 revision from step 13, increment 0, 2026-11-01, decided by the user)

- **One query reads one snapshot.** A query that reads several aggregate tables (`help_request` and `asked_provider` for getHelpRequestById) runs in one read-only REPEATABLE READ transaction, so a concurrent command never shows half applied (Fagan D-06; `JdbcHelpRequestQueriesIT`).

## Addition (later question L6, step 11 of increment 1, 2026-11-04, decided by the user)

- **Notes live with the aggregate.** The help request keeps the notes written on it (cancellation, rejections) with kind, text and time; they are stored with its other tables in the same transaction. `notes` and `previousNotes` (INV-07: the requester's earlier requests on the same step of the same recipe) are read from the aggregate tables, so they keep read-your-writes (QS-07). No projector and no projection tables in increment 1.
- **Keyset paging, oldest first.** Lists are ordered by the UUIDv7 id, ascending (creation order; OpenAPI 1.0.8). The adapter fetches `limit + 1` rows to know whether a next page exists. `nextCursor` is opaque (base64url); a cursor the API did not issue answers 400 `VALIDATION_FAILED`.
- **List access as for single reads.** Any caller with `help:read` may list, with or without the `requester` filter (providers look for open requests). The privacy of listing another Cook's requests is a question for the security review.
- **Chef helps** are left out for everyone but the requester from increment 4 on, when chef helps can exist (INV-01: never for a preparation step).

## Addition (step 5 revision from step 14, increment 1, 2026-11-08, decided by the user)

- **`previousNotes` shows the 10 most recent notes, oldest first** (security review SEC-14; INV-07; OpenAPI 1.0.12). The limit is applied in the query, so a list page stays bounded however many requests a Cook makes on one step. An index on (requester, recipe, how_to_step) is on the planned list. `notes` of a single request has no such limit: it holds one cancellation note and one note per rejected help, so it cannot grow the way the history did (planned list).
