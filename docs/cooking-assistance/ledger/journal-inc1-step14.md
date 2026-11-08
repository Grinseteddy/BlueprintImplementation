# Journal — increment 1, step 14 (Security review, OWASP)

Board B3, US-CA-03. EXACT Coding at 88fb3f9, autonomy full-hitl. Branch
`feature/cooking-assistance-inc1-security` from main 8ac22bb. 2026-11-08.

## Review (separate agent, Fable, reviewer mode of owasp-security-reviewer)
- Package: increment diff against bba2606, OpenAPI 1.0.11, AsyncAPI 1.0.7, ADR0012, 0013, 0015, 0019,
  0020, 0021, invariants, identities, limits, accepted and deferred items. No ledger, journals or
  reasoning. Target: a copy of the repository on Testcontainers; 209 HTTP probes, 13 AMQP publishes.
- Result: 20/20 categories with a verdict; critical 0, high 0, medium 2 (SEC-14, SEC-06, deferred
  from increment 0), low 4 (SEC-15 to SEC-18). Tools from PR #5: Dependency-Check, SpotBugs +
  FindSecBugs, Schemathesis green; the only Dependency-Check hit is a false positive.
- I checked SEC-06, the raw path in the MDC and SEC-14's unlimited queries in the code before
  presenting. SEC-15 to SEC-18 rest on the reviewer's probes and reading.
- Decisions (user): fix SEC-06, 14, 15, 16; accept SEC-17 (ADR0012, owner Annegret Junker); defer
  SEC-18 to increment 4.

## Fix cycles
Test list L1–L6, P1, W1, W2, M1, M2 approved; L5b and L7 added. Every Red for the predicted reason.
- SEC-06 (steps 9, 12): L1 401 and L2 403 in `SecurityConfiguration`; L3 domain 403s, 422 and 409
  silent (green on activation, trial logging every rejection failed only that test); L4
  `NotAuthorized`; L5 dead letter; L5b forged message id; L6 UUID-only MDC (UUID case green on
  activation, trial failed only that test).
- Decisions on the way (user): domain 403 lines carry status, code and rule only (the handler has no
  request); a dead letter names the rejection's type, not its text.
- SEC-14 (steps 3, 4, 5, 11): P1, 12 for 10. SEC-15 (step 9): W1, W2; Spring Security has no switch to
  turn the endpoint off, so the agreed fallback applies. SEC-16 (steps 5, 12): M2 written before the
  setting so that it had a real Red; approved tests moved to the management port.
- SEC-19 (found by the re-test, step 9): L7, at most the path's first 200 characters.

## Mistakes and slips
- L2 first required the token's subject on the 403 line; I wrote that requirement without checking
  ADR0019 ("without personal data"). The refactor agent found it. L2's check flipped (approved test
  changed, user), Red, subject removed.
- L5's first draft asserted the absence of a phrase that is not in the fixture, so that part could
  not fail; corrected before the Green and the Red rerun.
- W2 first asserted only the absence of the host; strengthened to the exact value (user), trial
  failed only W2.

- The unbounded URI in the refusal lines (SEC-19) was my own code from SEC-06.

## Refactoring (isolated agents, `src/test` unchanged by `diff -r`, all kept by the user)
- SEC-06 group: logger field above the `READ_PATHS` comment; a shared UUID helper and a one-call
  method tried and undone.
- SEC-14, 15, 16 group: `NOTES_OF_EARLIER_REQUESTS_ON_THE_STEP` separates INV-07 from the limit;
  `publishedResource` names the metadata customizer.
- L7 (`loggablePath`) had no agent pass: one small method after the group reviews.

## Reverse check and re-test
- Reverse check (Sonnet, read-only): 35 claims, no code defect, 4 wording drifts corrected, 1 gap
  recorded (`notes` per request).
- Re-test (Fable, 161 HTTP, 10 AMQP): SEC-06, 14, 15, 16 fixed, no regression; new SEC-19. L7 is
  covered by its test only.

## Final gates (2026-11-08)
- `./mvnw -o clean verify`: 332 + 137, 14 + 42; Spotless, Error Prone, Spectral. Fault injection (exact `quality.yml` command, offline, before L7): 3 green, E5b 1.2 s. Intent coverage: exit 0, no gaps; INV-03 without a domain-level test.
