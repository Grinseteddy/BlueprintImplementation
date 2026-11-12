# Journal — increment 2, step 14 (Security review, OWASP)

Board B2 "Help with ingredient substitution", US-CA-02. Reviewer a separate agent (Fable, `owasp-security-reviewer`, reviewer mode) on a repository copy at e1c840a with probe ITs on Testcontainers; moderator the orchestrating session; decider the user. Branch `feature/cooking-assistance-inc2-review`.

## Review
- Package: increment diff 8bbc221..e1c840a, OpenAPI 1.0.15 / AsyncAPI 1.0.11, ADR0012/0013/0015/0016/0019/0020/0021, the actors and command → actor table, increment 1's security report, the step 13 report, PR #8's CI artifacts.
- 20/20 categories: 14 no finding, 1 not applicable (API7), findings in API4, API10, A03, A06, A08, A10. 179 HTTP of 250, 15 AMQP of 20; the original repository unchanged.
- SEC-20 high (amount unbounded: 1e1500 makes every later read and command of the request 500), SEC-21 medium (above Double.MAX the HelpProvided event is dead-lettered), SEC-22 low (Grandma's 1e400 retried five times), SEC-23 low (load-tests outside the scan, pip/npx without hashes). SEC-18 widened. O-1, O-5.

## Decisions and fixes
- User: an amount is at most 10,000 with at most 3 decimal places; SEC-21/22 through the same bound; SEC-23 deferred; SEC-18 widening recorded; O-1, O-5 planned.
- The spec maximum is breaking for QS-15 (oasdiff `request-property-max-set`) — a decision point; user: spec maximum with a named, path-scoped gate exception (ADR0014 addition; `tools/contract/oasdiff-err-ignore.txt`).
- Documents first (step 3 r19, ADR0014 addition, CHANGELOG), shown and approved.
- REST test (10000.001, 1e400, 1e1500 → 400): Red 201 as predicted; Green OpenAPI 1.0.16.
- Consumer test (10000.001, 1e400 → DEAD_LETTER): Red ACK and RETRY as predicted; Green AsyncAPI 1.0.12.
- Domain tests: above 10,000 → Red, Green `MAXIMUM_AMOUNT`; more than 3 decimal places → Red, Green `MAXIMUM_DECIMAL_PLACES` (scale after stripping trailing zeros). Prediction slip: a test count predicted (72, was 73 — a parameterized test counts once while disabled).
- Gate trials: without the exception exit 1 (OpenAPI first, then the AsyncAPI payloads — the second line added after the first run showed the synthetic path `POST /consumeGrandmaHelpProvided`); with both lines exit 0; LITER removed in either spec exit 1.
- `verify` green (502 + 189, 14 + 69).

## Re-test and reverse check
- New reviewer (Fable): SEC-20/21/22 fixed, no regression, no new finding; the exception narrow (maximum 5000 or LITER removed still fail); O-7 (after one rejection the other Helps cannot be accepted, 409) and the read-side residual.
- Reverse check of the fix: ADR0014 "minor" → "non-major", QS-15 note, CHANGELOG quote; two tests added (user): bounds accepted (domain, green on activation, trial with exclusive bounds → 3 × NoSubstituteProposed), 0.0001 → 422 INV-12 (REST, green on activation, trial without the decimal rule → 201; message wording "Response status expected" counted as matched, as decided earlier).
- Final `verify` green (506 + 189, 14 + 69; JaCoCo, Spotless, Error Prone, Spectral); intent coverage without gaps; report `reviews/increment-2-security.md`.
- README (status, commands, CI jobs) and CLAUDE.md (increment, commands) brought up to date for the increment checkpoint.
