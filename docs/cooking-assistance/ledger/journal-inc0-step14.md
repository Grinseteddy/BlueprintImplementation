# Journal — increment 0, step 14 (Security review, OWASP)

## Review (separate agent, Fable, reviewer mode of owasp-security-reviewer)
- Package: specs (OpenAPI 1.0.5, AsyncAPI 1.0.2), ADR0012/0013/0019 and others, actors and command → actor table, test identities, manifests, deferrals D-07/R-02, questions Q-01/Q-02/Q-05 from step 13. Observation handed over: ADR0019's Dependency-Check not wired.
- Target: a copy of the repository on Testcontainers (≈ 70 HTTP probes, 2 AMQP publishes, one restricted broker user); repository untouched.
- Result: 20/20 categories with a verdict; critical 0, high 1 (SEC-01), medium 6 (SEC-02…07), low 4 (SEC-08…11). Report: reviews/increment-0-security.md.
- I spot-checked SEC-02, SEC-03, SEC-05 in the code before presenting.
- Decisions 2026-11-01: fix SEC-01, 02 (+D-07), 03, 04 (ADR0012 amended: the module declares what it owns), 05, 07, and the lows 08, 09, 10; defer SEC-06 (increment 1, QS-14 logging); SEC-11 and the missing tools as proposals at the increment checkpoint.

## Fix cycles — step 9 group
- SEC-03: Red 201 instead of 401 as predicted; Green exp required (JwtClaimValidator). ADR0012 addition.
- SEC-02 + D-07: **mismatch** — predicted 200, got 404 (EdgeTest stores no request; same cause: HEAD passes without help:read). Green HEAD under the read rule. D-07 gate (every spec operation without its scope → 403) green on activation as predicted; trial without "/helps" → only listHelps fails with 501 as predicted.
- SEC-08: Red ACK instead of DEAD_LETTER as predicted; Green via the spec: AsyncAPI 1.0.3 GrandmaMessageHeader (source const grandma-avatar), the consumer validates against it (step 4 r7).
- SEC-09: Red 500 (IllegalStateException "operation handle is not in the spec") as predicted; Green OPTIONS → 405 with the methods the spec declares for the path.
- Refactor (agent): READ_PATHS for GET and HEAD, OpenApiOptionsRefusal as its own interceptor — kept. Proposals → planned list (methodsDeclaredFor and path-level keys; HEAD in Allow). Full reactor verify ×2 green. Commit 732f4c3 (with ADR0012 amendment and both review reports).

## Fix cycles — SEC-07 (steps 3, 4, 6, 9)
- T1 domain: Red "Expecting code to raise a throwable" as predicted; Green https guard (INV-38, PictureLinkNotSecure, INVALID_PICTURE_LINK).
- T2 REST: Red 422 instead of 400 as predicted (only the domain guard caught it); Green OpenAPI 1.0.6 pattern `^https://` on Pictures, code named.
- T3a consumer: green on activation as predicted; trial without the domain guard → exactly the domain test and T3a fail, as predicted.
- T3b contract: Red "Expecting actual not to be empty" as predicted; Green AsyncAPI 1.0.4 pattern on images.
- Step 3 r3: INV-38 extended. Refactor (agent): one INV-38 guard (ensurePicturesMayBeAttached), isHttpsLink — kept; proposal Picture value object (increment 4, B4-S01). Commit 56c17d8.

## Fix cycles — step 12 group
- SEC-01: Red 201 instead of 413 as predicted (a draft of the test carried scratch lines; cleaned before the run); Green 1 MB limit in the edge's body reading (Content-Length and readNBytes), PAYLOAD_TOO_LARGE, OpenAPI 1.0.7.
- SEC-10: Red link http://evil.example:8443/… as predicted; Green links from cooking-assistance.public-base-url (LARDER_PUBLIC_BASE_URL in larder-app); test property http://localhost keeps the approved link assertion unchanged.
- SEC-05: **process slip** — both binding tests written active at once (both failed for the predicted reason). **Mismatch at Green:** Boot's binder leaves an unresolved placeholder as a literal instead of failing, so "fails on startup" did not hold. User chose a real startup check: test rewritten with ApplicationContextRunner (Red "to have failed" as predicted; **second slip:** the counter-test was active at once, green as a guard); Green BrokerEnvironmentCheck (getRequiredProperty ×3); trial with a fourth required variable → only the counter-test fails, as predicted.
- SEC-04: **mismatch in location** — predicted initialize() to throw, the context already failed at startup (same cause: configure on grandma-avatar refused). Green: no grandma-avatar declaration, binding by name; test brokers declare the exchange as its owner (fixture TestBroker). **Mismatch at Green:** the queue's dead-letter exchange "" needs write on amq.default; user chose the module's own DLX cooking-assistance.dlx (ADR0015 addition). Then full reactor verify green.
- Observed during the build: an Error Prone warning (default charset) in existing larder-app test code → minors.
- Refactor (agent): Javadoc back on its method in ProblemDetailsAdvice, readWithinSizeLimit (both size checks in one place), @PublicBaseUrl (the property key written once), REQUIRED_BROKER_VARIABLES — kept 2026-11-02; trial "Links as an injected component" undone (needs the frozen test configuration changed) → planned list, increment 1. Tests unchanged against the snapshot; full reactor verify ×2 green.

## Step 12 suites and intent coverage, reverse check of the fixes
- Full reactor verify ×2 green (166 + 55; 3 + 29, 12 skipped = planned scenarios). Intent coverage: exit 0, no gaps, US-CA-03 done for the skeleton slices (prediction held); report updated.
- Scoped reverse check (Sonnet, 9254635..HEAD): no defect, 5 drifts. Decisions 2026-11-02: (1) LARDER_* variables → README at the increment checkpoint; (2, 3) documented — INV-38 step 3 r4 (scheme case-insensitive in the domain, specs stricter), CHANGELOG note (REST sees 400 before 422); (4) 413 stays in the Problem.code list like 406/415; (5) ADR0012 addition (HEAD under help:read; bind by name instead of "passive declaration"), ADR0019 addition (1 MB, OPTIONS, public base URL, broker from the environment).

## Security re-test (separate agent, Fable)
- 8 findings fixed (verified by probe), SEC-09 partially (/error → 500), SEC-06/11 unchanged; new SEC-12, SEC-13 (low). I confirmed the three open claims in the code (OpenApiContract throws for the "error" operation; consumer catch-all retries the URI IllegalArgumentException; getRequiredProperty accepts ""). Decisions 2026-11-02: fix all three now, test-first; observations → planned list.

## Fix cycles — re-test findings
- T1 SEC-09 rest (step 9, EdgeTest#errorPathIsNotFound): Red 500 instead of 404 as predicted ("operation error is not in the spec"); Green: a handler the spec does not declare → NoResourceFoundException → 404 NOT_FOUND. Forwarded errors keeping their status → planned list.
- T2 SEC-12 (step 9, GrandmaHelpProvidedConsumerTest#unparsablePictureLinkIsDeadLettered): Red RETRY instead of DEAD_LETTER as predicted; Green: GrandmaAnswerMapper turns an unparsable link into InvalidMessage.
- T3 SEC-13 (step 12, BrokerSettingsTest#hostWithEmptyBrokerHostDoesNotStart): Red as predicted (context starts); the assertion message was corrected before the run ("Expecting actual not to be null", not "to have failed") — the list wording named another assertion. Green: required and non-blank for all three broker variables.
- Refactor (agent): one operation lookup in OpenApiContract (operationFor), the SEC-09 refusal as its own interceptor OpenApiUndeclaredHandlerRefusal (OpenApiParameterValidation back to its committed state) — kept 2026-11-02; no change to GrandmaAnswerMapper, BrokerEnvironmentCheck. Tests unchanged against the snapshot; full reactor verify green (168 + 55; 4 + 29, 12 skipped). Proposals (unit test for the refusal, interceptor order test) → planned list.
