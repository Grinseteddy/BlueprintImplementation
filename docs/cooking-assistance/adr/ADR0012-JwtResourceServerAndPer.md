ADR0012

# JWT resource server and per-module broker user

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will validate OAuth 2.0 bearer JWTs in the module (issuer `https://larder.org`, audience `cooking-assistance`, keys from JWKS, 60 s clock skew), connect to RabbitMQ with the module's own user limited to writing the `cooking-assistance` exchange and reading its own queues, and hand the identity to the application as an `Actor` value (id, roles, premium).**

## Context
Callers are cooks, chefs and community members through the AppShell (ADR0005), plus the Grandma Avatar through the broker. The OpenAPI and AsyncAPI security schemes (step 4) match this decision.

## Options considered

### Service validates JWTs

defense in depth; no trusted header

### Gateway validates, module trusts a header

one place to validate, but a forged header bypasses it

## Consequences

- No framework security type reaches the domain
- Broker credentials come from the environment

## Principles
AP0008 (honors)

## Quality scenarios
QS-12, QS-13

## Addition (step 5 revision from step 14, increment 0, 2026-11-01, decided by the user)

- **Every token expires.** A token without `exp` is refused (security review SEC-03).
- **The module declares what it owns; it never declares what other contexts own** (security review SEC-04). Its broker user gets, as a regular expression per RabbitMQ permission:
  - configure `^cooking-assistance(\..*)?$` — the `cooking-assistance` exchange and the module's queues (`cooking-assistance.grandma-help-provided`, `cooking-assistance.dlq`);
  - write `^cooking-assistance(\..*)?$` — publish to its exchange, bind its own queues;
  - read `^(cooking-assistance\..*|grandma-avatar)$` — consume its own queues, bind a queue to the Grandma Avatar's exchange.
- The `grandma-avatar` exchange is declared by its owner. The module only checks that it exists (passive declaration) and never gets configure or write rights on it.
- A test runs the module with a broker user holding exactly these permissions.
