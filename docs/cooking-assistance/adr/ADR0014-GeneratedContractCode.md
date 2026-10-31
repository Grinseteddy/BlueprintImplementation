ADR0014

# Generated contract code

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will generate the REST interfaces and DTOs with `openapi-generator-maven-plugin` 7.x (generator `spring`, `interfaceOnly`, `useSpringBoot3`) and the message models with AsyncAPI Modelina via the AsyncAPI CLI (Java records), both pinned, into `target/generated-sources`. Generated code is never edited.**

## Context
The approved specs (step 4) are the contract; drift must fail the build.

## Options considered

### Generate from specs

spec drift becomes a compile error

### Hand-written DTOs

drift goes unnoticed

## Consequences

- Versions are pinned in the POM; the generator run is part of `./mvnw verify`

## Principles
AP0005

## Quality scenarios
QS-15
