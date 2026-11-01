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

## Addition (step 9, increment 0, 2026-10-31, decided by the user)

- OpenAPI: `openapi-generator-maven-plugin` 7.26.0, generator `spring` with `interfaceOnly`, `useSpringBoot3`, `useTags`, `skipDefaultInterface` (a new operation fails compilation until a controller serves it), `useBeanValidation`, `openApiNullable=false`, `hideGenerationTimestamp` (reproducible output).
- AsyncAPI: Modelina 5.10.1 through its library API (`modelType: 'record'`), not the AsyncAPI CLI, whose `generate models` command cannot emit records. The tool package `tools/codegen/` pins Modelina in its lockfile; `frontend-maven-plugin` 1.15.1 installs Node v22.22.1 and runs it at `generate-sources`. The script fails the build when nothing is generated or a schema gets an invented `AnonymousSchema` name.
- Modelina supports AsyncAPI up to 3.0.0, so the spec declares 3.0.0 (step 4 r3).
- Unbuilt operations are implemented explicitly as 501 NOT_IMPLEMENTED and replaced when their increment wires them.
