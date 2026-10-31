ADR0007

# Cook Assistance as a Maven module of the Larder monolith

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will build Cook Assistance as the Maven module `cooking-assistance`, hosted by a thin Spring Boot application `larder-app`.**

## Context
ADR0001 makes the Bounded Contexts modules of one monolith. No Larder codebase exists yet.

## Options considered

### Maven multi-module: `cooking-assistance` + `larder-app`

honors ADR0001 now; other contexts join as sibling modules

### Standalone application, merged later

contradicts ADR0001 and AP0001 until merged

## Consequences

- `larder-app` holds only configuration and the composition root
- The module exposes its REST API under `/cooking-assistance`

## Principles
AP0001 (honors), ADR0001

## Quality scenarios
QS-02
