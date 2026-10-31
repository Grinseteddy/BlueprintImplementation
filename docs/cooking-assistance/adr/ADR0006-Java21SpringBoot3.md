ADR0006

# Java 21, Spring Boot 3.5 and Maven

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will implement Cook Assistance in Java 21 with Spring Boot 3.5, built with Maven.**

## Context
Steps 6–12 of the implementation blueprint need one stack. EXACT Coding ships a stack profile for Java + JUnit 5 + Maven. Larder is a monolith (ADR0001), so this choice is the default for the modules that follow.

## Options considered

### Java 21 + Spring Boot 3.5 + Maven

matches EXACT's profile; Spring Modulith and ArchUnit verify module boundaries; widely known

### TypeScript + NestJS + Vitest

matches EXACT's other profile; weaker module-boundary tooling for a JVM-free monolith

### Kotlin + Spring Boot + Gradle

no EXACT profile (Java profile adapted)

## Consequences

- Every Larder module uses this stack unless a later ADR says otherwise
- Error Prone and Spotless are available for the hooks

## Principles
AP0001, AP0003, AP0005

## Quality scenarios
QS-01, QS-02, QS-16
