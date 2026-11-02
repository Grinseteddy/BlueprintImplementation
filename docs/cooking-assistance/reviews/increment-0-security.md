# Security report — Cooking Assistance increment 0 (walking skeleton), reviewer Claude Fable 5.1

## Package and target
Received: package.md (scope US-CA-03, B3-S01/B3-S02), OpenAPI 1.0.5, AsyncAPI 1.0.2, ADR0010/0012/0013/0015/0019/0020, invariants.md, actor table, test identities (TestTokens/TestSigningKeys), build files, verify.yml, .pre-commit-config.yaml, accepted risks and deferrals D-07/R-02, questions Q-01/Q-02/Q-05.
Target: a copy of the repository at HEAD 9254635 (scratchpad/security/repo-copy, original untouched, `git status` clean), larder-app started by a probe IT (`SecurityProbeIT`, RANDOM_PORT) on Testcontainers PostgreSQL 16 + RabbitMQ 3.13, built with `mvn -o` (AsyncAPI models generated with the original pinned toolchain, read only). Limits: ≤ 80 HTTP requests, 2 AMQP publishes, one broker user added to the throwaway container. No network, no ZAP, no Dependency-Check, no SpotBugs (not installed), gitleaks not installed locally.

## Category verdicts
| Category | Verdict | Reason |
|---|---|---|
| API1 Broken Object Level Authorization | checked — no finding | own request → 403 OWN_REQUEST; not-asked provider → 403 PROVIDER_NOT_ASKED; unknown id → 404; chef on unknown id → 403 before 404 (ADR0013 order). GET by id for any help:read is the step-11 decision. |
| API2 Broken Authentication | finding SEC-03 | expired (-120 s), nbf future, alg none, HS256 confusion, wrong aud/iss, non-UUID sub, malformed, no token → all 401; a token **without exp** → 200. |
| API3 Broken Object Property Level Authorization | checked — no finding | extra `requester`/`status`/`helpRequestId` in HelpRequestCreate ignored (requester stays the token's sub); helpProviderType CHEF/GRANDMA_AVATAR via REST → 403; response field set equals the spec's. |
| API4 Unrestricted Resource Consumption | finding SEC-01 | 3 MB body accepted (ADR0019: 1 MB). Rate limiting = accepted risk (ADR0019). |
| API5 Broken Function Level Authorization | finding SEC-02 | HEAD serves getHelpRequestById without help:read; otherwise role/scope table holds (community cannot create, chef cannot answer, unknown role grants nothing, read-only cannot POST). |
| API6 Unrestricted Access to Sensitive Business Flows | checked — no finding | creation/answer volume protection is the gateway's (ADR0019 assumption, still valid); nothing in the module contradicts it. |
| API7 Server Side Request Forgery | not applicable | no outbound call takes client input; JWKS URL from env; image URIs are stored, never fetched (see SEC-07 before any fetcher is built). |
| API8 Security Misconfiguration | findings SEC-05, SEC-10 | headers present (nosniff, X-Frame-Options DENY, no-store), CSRF off on a stateless bearer API, no CORS, actuator exposes health only, errors carry no internals; but broker connection falls back to defaults and Host is reflected into links. |
| API9 Improper Inventory Management | checked — no finding | unbuilt operations → 501/400 VALIDATION_FAILED; `/actuator` (authenticated) lists only health; env/mappings/api-docs 404; swagger/unknown paths 401 unauthenticated. |
| API10 Unsafe Consumption of APIs | findings SEC-07, SEC-08 | Grandma messages validated against the AsyncAPI schema but trusted from any writer; image URI schemes unrestricted. |
| A01 Broken Access Control | finding SEC-02 (by reference) | API1/API3 clean, API5 finding. |
| A02 Security Misconfiguration | findings SEC-05, SEC-11 | API8 plus delivery config; no container/deployment manifest exists in the repository to review. |
| A03 Software Supply Chain Failures | finding SEC-11 (low) | versions read from `mvn -o dependency:list` (unverified by tool): Boot 3.5.6 / Framework 6.2.11 / Security 6.5.5 / Tomcat 10.1.46 / Jackson 2.19.2 / nimbus 9.37.4 / pgjdbc 42.7.7 / Flyway 11.7.2 / amqp-client 5.25.0 / json-schema-validator 1.5.9; generators pinned (openapi-generator 7.26.0, Modelina 5.10.1 + lock, Node v22.22.1). |
| A04 Cryptographic Failures | checked — no finding | no committed secrets (grep), test key generated per run, RS256-only key selector, JWKS from env; TLS is terminated outside the module (assumption, see SEC-05 for amqps). |
| A05 Injection | checked — no finding | every SQL is a parameterised JdbcClient/Spring Data JDBC statement; jsonb via PGobject; Problem `type` built from constant codes only. |
| A06 Insecure Design | finding SEC-04 | broker identity of ADR0012 cannot run the code; trust in the grandma queue rests on ACLs nothing in the repo expresses. |
| A07 Authentication Failures | finding SEC-03 (by reference) | ADRs define no lifetime/revocation beyond exp. |
| A08 Software or Data Integrity Failures | checked — no finding | Jackson polymorphism closed (`@JsonSubTypes`, Id.NAME), no default typing; messages schema-validated; CI `permissions: contents: read`; SHA pinning → SEC-11. |
| A09 Security Logging & Alerting Failures | finding SEC-06 | 20 authentication/authorization refusals produced zero log lines. |
| A10 Mishandling of Exceptional Conditions | finding SEC-09 | OPTIONS → 500 with stack trace; otherwise fail-closed (401 before body, 403 before load, retry-once with rollback). |

## Findings

```
SEC-01 · high · API4 Unrestricted Resource Consumption
Location:  POST /help-requests, POST /help-requests/{id}/helps; cooking-assistance/.../adapter/in/rest/OpenApiRequestValidation.java:49 (readAllBytes); larder-app/src/main/resources/application.yml (no server.* limit)
Violates:  ADR0019 "limit request bodies to 1 MB"
Evidence:  as Lena (cook, help:write) POST /help-requests with a 3 MB JSON body (unknown field "padding") → 201 in 72 ms,
           help request created. Same body with 3 MB in description → 400 only after the whole body was parsed.
           Unauthenticated 3 MB body → 401 (body not read). No max-http-post/request-size or filter anywhere in the repo.
Abuse-case test:
  Given a valid cook token
  When POST /help-requests carries a 1 MB + 1 byte JSON body
  Then the response is 413 Problem Details (code to be added to the spec's error map) and nothing is stored
  -- B3-S01 US-CA-03 OWASP-API4
Owning step: 12 (composition/infra: server limit), 4 (spec: 413 entry)
```

```
SEC-02 · medium · API5 Broken Function Level Authorization
Location:  HEAD /help-requests/{helpRequestId}; cooking-assistance/.../adapter/in/rest/SecurityConfiguration.java:44-51
Violates:  ADR0013 (scopes checked at the inbound adapter, deny by default), OpenAPI security help:read on getHelpRequestById; concrete instance of deferred D-07
Evidence:  matchers name POST and GET only; HEAD falls to anyRequest().authenticated() and Spring MVC serves HEAD with the GET handler.
           HEAD /help-requests/{id} with Jonas' token scope help:write → 200; with scope "" → 200; unknown id → 404.
           GET with the same tokens → 403 INSUFFICIENT_SCOPE. An authenticated caller without any scope learns which ids exist.
Abuse-case test:
  Given Lena's help request and a token with scope help:write only
  When HEAD /help-requests/{id}
  Then the response is 403 INSUFFICIENT_SCOPE (and every method not in the spec is 405, never authenticated-only)
  -- B3-S01 US-CA-03 OWASP-API5
Owning step: 9 (inbound adapters)
```

```
SEC-03 · medium · API2 Broken Authentication
Location:  cooking-assistance/.../adapter/in/rest/AcceptedTokens.java:21-28; SecurityConfiguration.java:72 (no-op Nimbus claims verifier)
Violates:  ADR0012 "validate … expiry"
Evidence:  JWT signed with the test key, iss/aud/sub/roles/scope set, no exp claim → GET /help-requests/{id} 200.
           JwtTimestampValidator only checks exp when present; the Nimbus verifier that would require it is disabled. Such a token never expires.
Abuse-case test:
  Given a correctly signed token without an exp claim
  When any operation is called with it
  Then the response is 401 UNAUTHENTICATED
  -- B3-S01 US-CA-03 OWASP-API2
Owning step: 9 (inbound adapters)
```

```
SEC-04 · medium · A06 Insecure Design (also API8)
Location:  cooking-assistance/.../adapter/in/messaging/AmqpConsuming.java:22-51, adapter/out/messaging/AmqpPublishing.java:16-19; ADR0012
Violates:  ADR0012 "module's own user limited to writing the cooking-assistance exchange and reading its own queues"; ownership of exchange grandma-avatar (Grandma Avatar context)
Evidence:  broker user with exactly the ADR permissions (configure "", write ^cooking-assistance$, read ^cooking-assistance\..*$) on the test broker:
           queueDeclare cooking-assistance.grandma-help-provided → 403 ACCESS_REFUSED configure; queueDeclare cooking-assistance.dlq → refused;
           exchangeDeclare cooking-assistance → refused; exchangeDeclare grandma-avatar → refused; queueBind → refused (write access to queue);
           basicGet own queue → allowed; publish to cooking-assistance → allowed; publish to grandma-avatar → refused.
           The module cannot start its RabbitAdmin declarations with the ADR identity; in practice it will be given configure rights on a
           foreign exchange, which is what makes Q-02's trust model hold or fail. The test instance runs as guest (administrator, .*), so no test exercises the ACL.
Abuse-case test:
  Given a broker user with the permissions ADR0012 (amended) names
  When larder-app starts and the relay publishes HelpRequestCreated
  Then all declarations succeed, the message is confirmed, and a publish to exchange grandma-avatar by that user is refused with 403
  -- B3-S02 US-CA-03 OWASP-A06:2025
Owning step: 12 (composition/infra), 5 (ADR0012 amendment: who declares what)
```

```
SEC-05 · medium · API8 Security Misconfiguration (A02)
Location:  larder-app/src/main/resources/application.yml:1-27
Violates:  ADR0012 "broker credentials come from the environment", ADR0019 "secrets only from environment variables", AsyncAPI server amqps://larder.org:5671 user-password
Evidence:  only cooking-assistance.security.jwk-set-uri is bound to ${LARDER_JWKS_URI} (fails fast when unset). No spring.rabbitmq.host/username/password/ssl
           placeholders exist: without SPRING_RABBITMQ_* env the host silently connects to localhost:5672 as guest/guest over plaintext AMQP.
           In the probe run rabbit.getConnectionFactory().getUsername() = guest. (Datasource has no placeholder either but Boot fails fast without a URL.)
Abuse-case test:
  Given larder-app started without broker environment variables
  When the context starts
  Then startup fails on an unresolved placeholder (LARDER_BROKER_*) and no connection to localhost:5672 is attempted; with variables set, the connection uses amqps
  -- B3-S02 US-CA-03 OWASP-API8
Owning step: 12 (composition/infra)
```

```
SEC-06 · medium · A09 Security Logging & Alerting Failures
Location:  SecurityConfiguration.java:79-102 (entry point / access-denied handler), ProblemDetailsAdvice.java:113-145, GrandmaHelpProvidedConsumer.java:38-43
Violates:  ADR0019 "log security events without personal data", ADR0020 (trace id on every log line), QS-14
Evidence:  the probe produced 11 × 401 (expired, none, HS256, aud, iss, sub, malformed, no token) and 9 × 403 (INSUFFICIENT_SCOPE, NOT_AUTHORIZED,
           OWN_REQUEST, PROVIDER_NOT_ASKED) plus one dead-lettered message path; the service log contains no line for any of them
           (only three LOG statements exist in main: ProblemDetailsAdvice:155, GrandmaHelpProvidedConsumer:46, OutboxRelay:54).
Abuse-case test:
  Given Lena answers her own request
  When the response is 403 OWN_REQUEST
  Then one WARN line is logged with code, operation, trace id and the rule id, and without subject, title or body
  -- B3-S02 US-CA-03 OWASP-A09:2025
Owning step: 9 (inbound adapters), 12 (log format/alert path)
```

```
SEC-07 · medium · API10 Unsafe Consumption of APIs (stored link content; A05 for the AppShell)
Location:  POST /help-requests/{id}/helps (HelpMapper.java:36-41), consumeGrandmaHelpProvided (GrandmaAnswerMapper.java:41-42), domain PreparationStepExplanation; OpenAPI/AsyncAPI Pictures `format: uri`
Violates:  design "Links to pictures" (glossary Picture, larder media); ADR0013 deny by default for content the context stores and republishes
Evidence:  Jonas POST helps with images ["javascript:alert(1)","file:///etc/passwd","http://169.254.169.254/latest/meta-data"] → 201;
           forged Grandma message with images ["javascript:alert(1)","http://evil.example/x.png"] → stored, request ANSWERED, HelpProvided carries the URIs.
           Any valid URI scheme passes; the cook's client will receive them as picture links.
Abuse-case test:
  Given Lena's open request to the Community
  When Jonas answers with images ["javascript:alert(1)"]
  Then the response is 400 VALIDATION_FAILED (REST) / the message is dead-lettered (broker) and nothing is stored or published
  -- B3-S02 US-CA-03 OWASP-API10
Owning step: 4 (spec: https-only picture URIs / allowed hosts), 6 (domain guard)
```

```
SEC-08 · low · API10 Unsafe Consumption of APIs
Location:  GrandmaHelpProvidedConsumer.java:28-37, AsyncApiMessageReader.validateHeaders
Violates:  ADR0012 trust boundary (Grandma Avatar is the only producer of grandma-avatar.help.provided); AsyncAPI MessageHeader.source "source system generating the event"
Evidence:  message published to the queue through the default exchange with header source "evil-system" and a valid payload → processed,
           Lena's request to GRANDMA_AVATAR shows status ANSWERED, askedProviders[GRANDMA_AVATAR].state ANSWERED. The only identity check is the
           schema (minLength 2, maxLength 20). The control is the broker ACL, which is outside the code and not described (SEC-04).
Abuse-case test:
  Given a message on cooking-assistance.grandma-help-provided whose source is not grandma-avatar
  When the consumer handles it
  Then it is dead-lettered and the help request stays OPEN
  -- B3-S02 US-CA-03 OWASP-API10
Owning step: 9 (inbound adapters)
```

```
SEC-09 · low · A10 Mishandling of Exceptional Conditions
Location:  OpenApiParameterValidation.java:31-33 → OpenApiContract.java:52; GET /error
Violates:  spec error map (no 500 for a well-formed request), QS-14 (alert noise)
Evidence:  OPTIONS /help-requests/{id} with any authenticated token → 500 INTERNAL_ERROR; log: "IllegalStateException: operation handle is not in the spec"
           with full stack trace (ProblemDetailsAdvice.handleUnexpected). GET /error with a token → 500 as well.
Abuse-case test:
  Given any authenticated caller
  When OPTIONS /help-requests/{id}
  Then the response is 200/204 with Allow (or 405), never 500, and no ERROR log line is written
  -- B3-S01 US-CA-03 OWASP-A10:2025
Owning step: 9 (inbound adapters)
```

```
SEC-10 · low · API8 Security Misconfiguration
Location:  cooking-assistance/.../adapter/in/rest/Links.java:11-16 (ServletUriComponentsBuilder.fromCurrentContextPath)
Violates:  OpenAPI servers https://larder.org/cooking-assistance (links are absolute, from the API's base URL)
Evidence:  raw HTTP POST /help-requests with "Host: evil.example:8443" → 201 {"helpRequestLink":"http://evil.example:8443/help-requests/…"};
           absolute-form request-target → link http://evil.example/…; X-Forwarded-Host/Proto ignored (no server.forward-headers-strategy), so behind
           the AppShell gateway links name the internal host and scheme http. Links are not copied into events (OutboxEventMapper), so the reflection reaches only the caller.
Abuse-case test:
  Given a request with Host: evil.example (or X-Forwarded-Host from an untrusted hop)
  When POST /help-requests succeeds
  Then helpRequestLink starts with the configured public base URL, never with the attacker's host
  -- B3-S01 US-CA-03 OWASP-API8
Owning step: 12 (base URL / forward-headers decision), 9 (Links)
```

```
SEC-11 · low · A03 Software Supply Chain Failures (A08 for CI)
Location:  .github/workflows/verify.yml:16-40, pom.xml:22
Violates:  A03 "pinned versions for generators and build plugins", A08 "CI steps not open to tampering"
Evidence:  actions/checkout@v4, setup-java@v4, setup-node@v4, gitleaks-action@v2, upload-artifact@v4 pinned by moving tag, not SHA;
           `npx -y @stoplight/spectral-cli@6.11.1` resolves transitive packages without a lock; `pip install pyyaml==6.0.2` without hash.
           Spring Boot 3.5.6 ships Tomcat 10.1.46; Boot 3.5.7 (Oct 2025) moves to 10.1.48 fixing CVE-2025-55752/55754/61795 — none reachable
           in this configuration (no rewrite valve, no multipart). Unverified by tool (Dependency-Check not run).
Abuse-case test (verification, no attacker):
  Given verify.yml
  When the workflow is read
  Then every third-party action is pinned to a commit SHA and every npx/pip install carries a lock or hash
  -- B3-S01 US-CA-03 OWASP-A03:2025
Owning step: 12 (composition/infra)
```

## Answers to the open questions
- **Q-01 — finding (SEC-04).** Yes, the code needs a wider identity than ADR0012 states. Demonstrated on the test broker: with exactly the ADR permissions every declaration in AmqpConsuming/AmqpPublishing is refused (configure on both queues and both exchanges; write on the queue for the binding), while publishing to cooking-assistance and reading the own queue work. The decision has to state either that declarations are an operations task (definitions file) and the module only verifies passively, or that the module user gets configure on `^cooking-assistance(\..*)?$` and read on `^grandma-avatar$` — and never configure/write on grandma-avatar (the probe shows the ADR user correctly cannot impersonate the Grandma Avatar).
- **Q-02 — finding, low (SEC-08), with the design dependency on SEC-04.** A forged message through the default exchange with `source: evil-system` was processed and marked Lena's request ANSWERED. The code has no identity check beyond the header schema; INV-01 (provider asked) and INV-36 still hold, so a forger can only answer requests that asked the Grandma Avatar. Acceptable only if the broker ACL is specified and tested (SEC-04); a `source` check is cheap defence in depth.
- **Q-05 — finding, low (SEC-10).** Host header and absolute-form request-target are reflected into helpRequestLink/helpLink; forwarded headers are ignored. Not exploitable against another user today (links are not persisted or sent in events), but the links will be wrong behind the gateway and the behaviour needs a decision (configured base URL or trusted forwarded headers).

## Tool triage
| ADR0019 names | Runs | Triage |
|---|---|---|
| OWASP Dependency-Check | not in build, not in CI | not run (no NVD download allowed); versions read from `mvn -o dependency:list` and judged from knowledge → SEC-11 (low, unverified by tool). Gap against ADR0019 itself: schedule as CI job. |
| gitleaks | pre-commit v8.28.0 and CI gitleaks-action@v2 | not installed locally; manual grep for private keys / passwords / secrets over the source tree: nothing (test keys are generated per run). No finding. |
| SpotBugs + FindSecBugs | not set up | not run; manual static review covered injection, deserialization, crypto (no findings there). Gap against ADR0019. |
| OWASP ZAP baseline | not set up | not run (excluded by boundary); replaced by 70 hand-written probes (results above). Gap against ADR0019. |
| Error Prone (ADR0018) | every compile | ran in the copy build; no security-relevant warnings in main. |
| Spectral (specs) | pre-commit and CI | not re-run; spec security schemes read manually: every operation carries oAuth2 scopes. |

## Earlier risks and deferrals
- Rate limiting at the AppShell/gateway (ADR0019 assumption): still valid; nothing in the module contradicts it; the body limit (SEC-01) is not covered by it.
- D-07 (operations without a scope rule fall to `anyRequest().authenticated()`): now has a concrete exploit (SEC-02, HEAD); resolve together.
- R-02 (non-standard status in the error fallback, `HttpStatus.valueOf`): still valid, not probed (needs a framework path that yields a non-standard code).
- Planned-list "mandatory publishing blocks an aggregate with no bound queue": observed — in the probe run (no queue bound to cooking-assistance) OutboxRelay logged 31 WARN stack traces in 18 s, one per pending row per second; log flooding joins SEC-06's logging decision.
- Planned-list "personal data in logged 500 stack traces": `handleUnexpected` and the consumer's retry WARN log exception messages that may contain header values or URIs; keep on the list with SEC-06.
- Observation (A10, no finding): a transient failure (DB hiccup) during consumption is retried five times within milliseconds (requeue without backoff) and then dead-lettered — ADR0015's letter, but valid Grandma answers can be lost until L5 replay exists.

## Not tested
ZAP, Dependency-Check, SpotBugs/FindSecBugs (not installed / not allowed); gitleaks locally; production JWKS endpoint and TLS termination (not in the repo); broker TLS (no config to test); R-02; rate limiting (accepted risk); chef-help visibility rules (not built in increment 0).

## Counts
critical 0 · high 1 (SEC-01) · medium 6 (SEC-02…SEC-07) · low 4 (SEC-08…SEC-11) · categories with verdict 20/20

## Probes run (reproducible)
Copy: `rsync -a --exclude target/ --exclude node_modules/ --exclude .git/` → scratchpad/security/repo-copy; AsyncAPI models generated with the original `tools/codegen/node/node generate-asyncapi-models.mjs` into the copy; build `./mvnw -o -B -pl cooking-assistance,larder-app -Dspotless.check.skip=true -Dskip.installnodenpm -Dskip.npm -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SecurityProbeIT -Dfailsafe.failIfNoSpecifiedTests=false verify` (run twice; logs scratchpad/security/probe-run.log, probe-run2.log; probe source repo-copy/larder-app/src/test/java/org/larder/security/SecurityProbeIT.java). Dependency versions: scratchpad/security/deps.txt.
Read only (no probe): domain, persistence adapters, outbox relay, Flyway migration, ADRs, specs, CI and hook configuration, generated code annotations.
Time spent: about 1 h 45 min.

## Decisions (user, 2026-11-01)
| Finding | Decision | Owning step |
|---|---|---|
| SEC-01 high | fix now: 413 above 1 MB, spec entry | 12, 4 |
| SEC-02 medium | fix now, with D-07 (spec-driven scope gate) | 9 |
| SEC-03 medium | fix now: exp required (ADR0012 addition) | 9, 5 |
| SEC-04 medium | fix now: ADR0012 amended — the module declares what it owns (configure/write `^cooking-assistance(\..*)?$`, read `^(cooking-assistance\..*|grandma-avatar)$`), grandma-avatar passive; test with a restricted broker user | 5, 12 |
| SEC-05 medium | fix now: broker settings from the environment, fail fast | 12 |
| SEC-06 medium | deferred to increment 1 (QS-14 logging work) | 9, 12 |
| SEC-07 medium | fix now: https-only picture URIs in both specs, domain guard | 4, 6 |
| SEC-08, SEC-09, SEC-10 low | fix now as one batch (SEC-08 via AsyncAPI 1.0.3 GrandmaMessageHeader) | 9, 4, 12 |
| SEC-11 low | proposal at the increment checkpoint (SHA pinning, Boot patch) | 12 |
| Tools (Dependency-Check, SpotBugs/FindSecBugs, ZAP baseline) | proposals at the increment checkpoint: CI jobs with time caps | 12 |
| Observations (relay log flooding, consumer retries without backoff, personal data in 500 logs) | planned list (increment 1) | 10, 9 |

The fix status, the re-test and the abuse-case tests are recorded in the ledger and in `ledger/journal-inc0-step14.md`.

## Re-test (separate agent, Fable, 2026-11-02)
Target: a copy of the repository at HEAD 9ee4dc1 (fixes 732f4c3, 56c17d8, 5e2a536), larder-app on Testcontainers PostgreSQL 16 + RabbitMQ 3.13, connected as a broker user with exactly the ADR0012 permissions through LARDER_BROKER_*; ≈ 200 HTTP requests, 13 AMQP publishes, 2 runs.

| Finding | Status | Evidence (probes) |
|---|---|---|
| SEC-01 high | fixed | 1048576 B → 201, 1048577 B → 413, chunked 1 MB+/3 MB → 413, all write operations; gzip not inflated (400); lying Content-Length → 400; 3 MB with Content-Length → connection closed by Tomcat (maxSwallowSize), nothing stored |
| SEC-02 medium | fixed | HEAD/GET on every read path without help:read → 403; path variants → 403/404/401 (firewall), no bypass |
| SEC-03 medium | fixed | no exp, exp as string/boolean/0/−1, nbf future, expired → 401 |
| SEC-04 medium | fixed | host runs as the ADR0012 user; publish/declare/delete on grandma-avatar and amq.default refused |
| SEC-05 medium | fixed | no variables → start-up fails naming the variable; default port 5671, TLS on |
| SEC-06 medium | deferred | ≈ 60 refusals, no security log line |
| SEC-07 medium | fixed | HTTPS://, javascript:, data:, //, http:, whitespace → 400; same in Grandma messages → dead-lettered |
| SEC-08 low | fixed | source missing/other/case/array → dead-lettered via cooking-assistance.dlx |
| SEC-09 low | **partially fixed** | OPTIONS → 405 with Allow; **GET/HEAD/POST /error with a valid token → 500 INTERNAL_ERROR** and stack trace ("operation error is not in the spec") |
| SEC-10 low | fixed | Host, X-Forwarded-*, Forwarded, absolute-form → links always from the public base URL |
| SEC-11 low | proposal | unchanged |

New findings:
- **SEC-12 · low · A10 (API10)** — a Grandma message with picture link `"https://"` passes the schema (`^https://`, format uri); `URI::create` throws IllegalArgumentException, classed as transient → 5 deliveries, 5 WARN stack traces, then dead letter (delivery limit). Nothing stored.
- **SEC-13 · low · API8** — `LARDER_BROKER_HOST=""` passes BrokerEnvironmentCheck; the host would connect to localhost:5671.

Observations: no maximum token lifetime; firewall rejections surface as 401; an existing queue with dead-letter exchange "" refuses the new argument until recreated; the read permission allows binding with "#" to every Grandma Avatar event.

Decisions (user, 2026-11-02): SEC-09 rest, SEC-12, SEC-13 fix now, test-first (steps 9, 9, 12); the four observations → planned list (token lifetime → ADR0012 inc 1; "#" bind → ADR0012 accepted risk; queue migration → README deployment note).
