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

## Addition (step 5 revision from step 14, increment 0, 2026-11-02, decided by the user)

- **Request bodies above 1 MB are refused** with 413 `PAYLOAD_TOO_LARGE`, before validation (security review SEC-01).
- **OPTIONS is refused** with 405 and `Allow` listing the methods the spec declares for the path (security review SEC-09).
- **Links in responses start with a configured public base URL** (`cooking-assistance.public-base-url`, in larder-app `LARDER_PUBLIC_BASE_URL`), never the request's Host or forwarded headers (security review SEC-10).
- **The broker connection comes from the environment**, TLS by default; the host refuses to start without host, user and password (security review SEC-05).
