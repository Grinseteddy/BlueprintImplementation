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
