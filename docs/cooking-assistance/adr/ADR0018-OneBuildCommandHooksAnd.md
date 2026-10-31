ADR0018

# One build command, hooks and CI

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will use the Maven wrapper with one command, `./mvnw verify` (generate, compile, lint, all tests, Spectral lint of both specs); Spotless (google-java-format) and Error Prone; `pre-commit` hooks for Spotless check, gitleaks and commitlint; GitHub Actions `verify` on every push and pull request.**

## Context
Step 0 chose GitHub Actions, pre-commit hooks and Conventional Commits. QS-16 caps the verify job at 10 minutes.

## Options considered

### Maven wrapper + GitHub Actions

one command locally and in CI

### Gradle

no EXACT profile

## Consequences

- Hooks never run the whole suite

## Principles
AP0005

## Quality scenarios
QS-16, QS-15
