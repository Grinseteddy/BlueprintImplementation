ADR0009

# PostgreSQL schema per module, Spring Data JDBC, Flyway

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will store the Help Request aggregate state-stored in PostgreSQL 16, schema `cooking_assistance` with its own database user, accessed with Spring Data JDBC and migrated with Flyway.**

## Context
ADR0002 settles one database instance with a schema and user per Bounded Context. D1 makes Help Request with its Helps one aggregate, which maps to one root table with child tables.

## Options considered

### State-stored tables + Spring Data JDBC

aggregate-oriented: loads and saves the whole aggregate, no lazy loading

### State-stored tables + JPA/Hibernate

lazy loading and dirty checking blur aggregate boundaries

### Event store

more moving parts; no event-sourcing need on the boards

## Consequences

- Tables: `help_request`, `help`, `asked_provider`, `note`, `outbox`, `processed_message`
- Migrations are additive (expand / contract)

## Principles
ADR0002 (honors option 1), AP0001

## Quality scenarios
QS-01, QS-05, QS-11

## Addition (step 7, increment 0, 2026-10-31, decided by the user)

- **A Help's answer is stored as `jsonb`** in `help.answer`, under the AsyncAPI field names. The four answer types arriving in increments 1–4 need no further migration.
- **Stored answers and outbox payloads follow one JSON rule set** (`AsyncApiJson`, ISO-8601 times), so the stored answer and the `HelpProvided` answer cannot drift apart.
- **The module's connection works in schema `cooking_assistance`** (`spring.datasource.hikari.schema`). The module's own database user is set up by operations; tests use the container's user.
