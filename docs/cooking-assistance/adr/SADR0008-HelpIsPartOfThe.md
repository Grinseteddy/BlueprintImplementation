SADR0008

# Help is part of the Help Request aggregate

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**Help is an entity inside the Help Request aggregate, not an aggregate of its own. This amends the Help model and SADR0004 (Help request / Help response).**

## Context
Every invariant of Help reads Help Request state, and accepting a Help changes both (blueprint step 2, decision D1).

## Options considered

### Entity in Help Request

one transaction for provider rules, answer matching and acceptance

### Separate aggregate

acceptance becomes a process with eventual consistency

## Consequences

- The REST API nests helps under their request (step 4, P4); `HelpProvided` keeps its name

## Principles
AP0003 (honors)

## Quality scenarios
QS-10, QS-01
