ADR0013

# Authorization: scopes, resource checks, actor invariants

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will check scopes (`help:read`, `help:write`) at the inbound adapter, resource rules in the application service (only the requester cancels, accepts or rejects; the caller's provider type must be asked; a chef's help is visible to the requester only), and actor rules as domain invariants (INV-36: a cook never answers their own request). Roles `cook`, `chef` and `community` and the premium flag come from token claims (premium source revisited in L1). Access is denied by default.**

## Context
AP0008 requires fine-grained access. The board names Cook, Chef, Community and Grandma Avatar as actors.

## Options considered

### Scopes + application checks + invariants

each rule lives where its data is

### External policy engine (OPA)

extra runtime for a small rule set

## Consequences

- 403 for a known resource the caller may not act on; 404 for an unknown id
- Step 14 tests each rule with an abuse case

## Principles
AP0008 (honors)

## Quality scenarios
QS-12, QS-13

## Addition (step 8, increment 2, 2026-11-09, decided by the user)
- **"The caller's provider type must be asked" is enforced in the aggregate** (`ProviderNotAsked`, INV-01), not in the application service: it depends on the request's state, and the aggregate holds it. The application service checks that the caller may answer as that provider type, before the request is loaded.
