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
