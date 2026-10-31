ADR0019

# Security baseline and review tools

### Adopted 2026-10-31

### Annegret Junker, Architect

## Decision
**We will take secrets only from environment variables, limit request bodies to 1 MB, keep Spring Security's default security headers, log security events without personal data, and use OWASP Dependency-Check, gitleaks, SpotBugs + FindSecBugs and an OWASP ZAP baseline scan for the step 14 reviews.**

## Context
AP0008 and QS-12. Step 14 runs from increment 0.

## Options considered

### Baseline now

the first security review has tools and a yardstick

### Decide at step 14

the skeleton is built without a baseline

## Consequences

- Rate limiting is left to the AppShell or gateway (recorded as an assumption)

## Principles
AP0008 (honors)

## Quality scenarios
QS-12, QS-13
