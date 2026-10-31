ADR0008

# Hexagonal package layout with architecture tests

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will structure the module hexagonally (`domain`, `application`, `adapter.in.rest`, `adapter.in.messaging`, `adapter.out.persistence`, `adapter.out.messaging`) and enforce it with ArchUnit.**

## Context
QS-02 requires a framework-free domain. AP0003 asks for DDD.

## Options considered

### Hexagonal + ArchUnit rules

the domain imports nothing from Spring, JDBC or AMQP; checked on every build

### Layered

dependencies point to persistence; weaker isolation

## Consequences

- The rule test fails the build on a violation

## Principles
AP0003

## Quality scenarios
QS-01, QS-02
