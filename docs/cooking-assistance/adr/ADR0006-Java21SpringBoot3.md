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

## Addition (increment 1, before step 6, 2026-11-02, decided by the user)

- **Spring Boot 4.1** (Spring Framework 7, Spring Security 7.1, Spring AMQP 4.1, Tomcat 11) replaces 3.5. Boot 3.5.16 was the last open-source 3.5 release; the Framework and Security fixes for the CVEs found in increment 0 exist only on the 7.x lines.
- **Jackson 2 stays for now** (`spring-boot-jackson2`, generator `useSpringBoot4` without `useJackson3`); the move to Jackson 3 is its own planned item.
- **Test stack:** JUnit 6, Testcontainers 2, Cucumber 8, jqwik 1.10, ArchUnit 1.5.
- **DPoP is off by default in Spring Security 7.1** (it is opt-in through `dPoP(…)`); the API stays bearer only (ADR0012), guarded by `HelpRequestsControllerTest#dPoPSchemeIsUnauthenticated`.
