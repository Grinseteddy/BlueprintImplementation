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
