ADR0017

# Test infrastructure: fast and slow suites on real infrastructure

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will use JUnit 5 and AssertJ; `./mvnw test` runs the fast suite without containers; `./mvnw verify` adds Testcontainers PostgreSQL 16 and RabbitMQ 3.13, reused per run. jqwik runs property tests (1,000 tries, fixed seed in CI), Cucumber-JVM runs one feature file per user story, and Schemathesis checks the OpenAPI contract.**

## Context
AP0005 demands 95 % automation. QS-04 and QS-05 need real infrastructure. The skeleton has cardinality rules (INV-01, INV-38).

## Options considered

### Testcontainers on real PostgreSQL and RabbitMQ

production-like

### H2 + embedded broker

dialect and protocol differences hide defects

## Consequences

- Docker is required for the slow suite

## Principles
AP0005 (honors)

## Quality scenarios
QS-01, QS-04, QS-05
