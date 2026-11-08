# Security report — Cooking Assistance increment 1 (Board B3, US-CA-03 slices B3-S02 ext., S03–S06), reviewer Claude Fable 5.1

## Package and target
Received: `package.md`; the repository copy `repo-copy/` at main 8ac22bb (increment diff `git diff bba2606 8ac22bb`, 190 files); OpenAPI 1.0.11, AsyncAPI 1.0.7; ADR0012, 0013, 0015, 0019, 0020, 0021 (with their increment-1 additions); invariants.md (INV-01…09, 36…39), boards-slices, quality scenarios QS-12/13/14; `reviews/increment-0-security.md` (SEC-01…SEC-13, decisions, re-test) and `reviews/increment-1-review.md`; test identities `TestTokens`/`TestSigningKeys`/`HelpRequests`; CI results of PR #5 (verify, contract, security). Not read: ledger, journals, conversation history.

Target: larder-app in the copy, started by two probe ITs (`larder-app/src/test/java/org/larder/security/SecurityProbeIT.java`, `SecurityProbe2IT.java`, RANDOM_PORT) on Testcontainers PostgreSQL 16-alpine and RabbitMQ 3.13 (the `grandma-avatar` exchange pre-declared as its owner would), time-out poller every 200 ms, outbox relay on, a Logback `ListAppender` on the root logger capturing every log line per probe. AsyncAPI models generated into the copy with the original's pinned toolchain (`tools/codegen/node/node generate-asyncapi-models.mjs`, read-only use). Build: `./mvnw -o -B -pl cooking-assistance,larder-app -Dspotless.check.skip=true -Dskip.installnodenpm -Dskip.npm -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false verify` (one Maven build at a time). Evidence ledgers: `probe-results.txt` (run 1, 158 HTTP, 13 AMQP), `probe-results-2.txt` (run 2, 51 HTTP, 0 AMQP), logs `probe-run.log`, `probe-run-2.log`, CI artifacts in `ci-artifacts/`.

Limits applied: 209 HTTP probes of ≤ 250, 13 AMQP publishes of ≤ 20; no load or denial-of-service run (the largest repeated set: 20 requests + 20 cancellations; the largest body: 1 MB + 11 bytes). Nothing outside local containers was touched; the original repository is unchanged (`git status`: only the pre-existing untracked `docs/cooking-assistance/ledger/wip/`).

Identities used: Lena (cook, requester), Tom (another cook), Jonas and Mia (community), a chef, tokens without scopes, read-only (`help:read`) and write-only (`help:write`), without roles claim, with unknown roles, expired, without `exp`, wrong audience/issuer, `alg=none`, tampered signature, malformed, non-UUID subject, 10-year lifetime.

## Category verdicts
| Category | Verdict | Reason / evidence (probe ids in the ledgers) |
|---|---|---|
| API1 Broken Object Level Authorization | checked — no finding | cancel/accept/reject by another cook → 403 NOT_REQUESTER (API1-01…03); provider accepting his own help → 403 (API1-04); a help of another request → 404 NOT_FOUND rule INV-05 (API1-05/06); unknown request → 404 before any actor check (API1-07/08); help under the wrong request → 404 (API1-14). Reads by any `help:read` holder are the step-11 decision (API1-09…13); their breadth is SEC-18 (A06). |
| API2 Broken Authentication | checked — no finding | no token, expired, no `exp`, wrong `aud`, wrong `iss`, `alg=none`, tampered signature, malformed, non-UUID `sub`, token in query string, token in form body, Basic → all 401 UNAUTHENTICATED (API2-01…11, 13). RS256-only key selector, 60 s skew, JWKS from `LARDER_JWKS_URI`. No maximum lifetime → SEC-17 (A07). |
| API3 Broken Object Property Level Authorization | checked — no finding | cancellation body with `status`, `requester`, `helpRequestId` → fields ignored, requester stays Lena (API3-01/02); help body with `isAccepted`, `accepted`, `helpProvider`, `helpRequester`, `helpId` → stored help has `isAccepted=false`, provider = token subject, ids generated (API3-08/09); `nextChef` → 501, state unchanged (API3-03); response key sets of HelpRequest and Help are subsets of the spec's schemas (API3-04/05). |
| API4 Unrestricted Resource Consumption | finding SEC-14 | limits hold at the edge: `limit` 0/101/−5 → 400, cursor > 500 chars or not issued → 400, bodies of 1 MB + 11 B → 413 on cancellation and rejection, notes > 2000 chars → 400 (API4-01…11). But `previousNotes` and `notes` are unbounded per response and per list item: 20 cancelled requests of one cook on one step → one list page of 464 KB with 190 notes (API4-14/15); quadratic growth. Rate limiting stays the gateway's (ADR0019 assumption). |
| API5 Broken Function Level Authorization | checked — no finding | community cannot create (403 NOT_AUTHORIZED), chef cannot answer as CHEF or COMMUNITY, cook without community role cannot answer, GRANDMA_AVATAR over REST refused (API5-01…05); no scope / read-only → 403 INSUFFICIENT_SCOPE on cancel, accept, reject; write-only → 403 on every read path incl. HEAD (API5-06…14); PUT/DELETE/PATCH/OPTIONS/GET on command paths → 405 with the spec's `Allow` (API5-16…21); unknown roles grant nothing (API5-23). A requester needs no role to cancel/accept/reject (API5-22): identity-bound by design (ADR0013). |
| API6 Unrestricted Access to Sensitive Business Flows | checked — no finding | request/answer/reject volume protection is the gateway's (ADR0019, accepted). The reject → answer → reject loop is bounded per cycle by INV-39 and the 2000-char note; its accumulation is covered by SEC-14. |
| API7 Server Side Request Forgery | not applicable | no outbound call takes client input; picture links are stored, never fetched, and `^https://` is enforced on both edges (A10-14, API10-07). JWKS URL from the environment. |
| API8 Security Misconfiguration | findings SEC-15, SEC-16 | headers present (nosniff, X-Frame-Options DENY, no-store, X-XSS-Protection 0), no CORS (no ACAO for a foreign Origin, preflight 401), Host/X-Forwarded-* not reflected into links (API8-01…15), 406/415 as Problem Details, TRACE 405, actuator root 401 unauthenticated and lists only health/prometheus when authenticated, env/loggers/info/heapdump 404 (API8-04…12). Open: `/actuator/prometheus` without authentication (SEC-16); the RFC 9728 metadata endpoint reflects Host (SEC-15). |
| API9 Improper Inventory Management | finding SEC-15 | `/error`, `/v3/api-docs`, swagger, `/helps/{id}`, `/help-requests/{id}/notes`, trailing slashes → 404; dot segments, `//`, matrix parameters → 400 (firewall); case variants of actuator paths → 401 (API9-01…12). But `GET /.well-known/oauth-protected-resource` is served unauthenticated and is in no spec (WK-01…06). |
| API10 Unsafe Consumption of APIs | checked — no finding | Grandma messages: valid → help stored (API10-01); redelivered id → no effect (02); `source` ≠ grandma-avatar, unknown request, provider not asked, 11 pictures, `javascript:` picture, non-UUID messageId, 201-char title, unbuilt answer kind → dead-lettered at once, `x-death.count=1` (03…09, 11); answer after CLOSED or time-out → acknowledged and ignored, INFO line with message id and state only (API10-12, S06-02). Observation: a malformed `traceparent` header dead-letters an otherwise valid answer (API10-10) — strict, not unsafe. The broker ACL of ADR0012 is tested by `BrokerPermissionsIT`. |
| A01 Broken Access Control | checked — no finding | by reference: API1, API3, API5 clean; design breadth of reads → SEC-18 (A06). |
| A02 Security Misconfiguration | findings SEC-15, SEC-16 (by reference) | API8; no container or deployment manifest exists in the repository to review (unchanged since increment 0); host fails fast on missing `LARDER_*` variables (`RequiredEnvironment`, settings tests). |
| A03 Software Supply Chain Failures | checked — no finding | Dependency-Check 13.0.0 in CI: one MEDIUM, CVE-2026-54285 on opentelemetry-api 1.62.0 → false positive (see triage); SpotBugs 4.10.5 + FindSecBugs 1.14.0: 0 instances in both modules; actions pinned to SHAs; Boot 4.1.2; Node v22.22.1 and `package-lock.json` pinned, `npm ci`; `pip install pyyaml==6.0.2` still without hash (rest of SEC-11, low hygiene, no new id). |
| A04 Cryptographic Failures | checked — no finding | no secrets in the tree (grep; gitleaks in CI green; `.gitleaksignore` names one historical throw-away CI broker password); signing key generated per test run; RS256 only; broker TLS on by default (`LARDER_BROKER_SSL`), port 5671; database and broker credentials only from the environment. TLS termination for HTTP is outside the module (assumption unchanged). |
| A05 Injection | checked — no finding | enum/uuid query parameters with SQL text → 400 (A05-01…04); cursor is base64url of a UUID, anything else → 400 (API4-05/06); injection-shaped note stored verbatim and returned JSON-encoded (A05-05/06); U+0000 → 400; wrong JSON types → 400 with the schema's message; every SQL statement in the diff is a parameterised `JdbcClient` statement (`JdbcHelpRequestQueries`, `JdbcHelpRequestRepository.claimDueIds`, `OutboxRelay`, `OutboxRetrySchedule`, `OutboxBacklogMetrics`). |
| A06 Insecure Design | finding SEC-18 | the read model takes no actor: every `help:read` holder gets every request with its notes and the requester's `previousNotes` (history), every help and every list (API1-09…13, API4-14/15). INV-08 gives the cancellation note to providers; INV-07 gives earlier notes to the cook on her next request; nothing gives a cook's history to every reader. Observation for increment 4: the spec's chef-visibility rule cannot be enforced by queries that do not know the caller. |
| A07 Authentication Failures | finding SEC-17 | a correctly signed token with `exp` = now + 10 years → 200 (API2-12). The increment-0 observation "no maximum token lifetime" was planned for ADR0012 in increment 1 and is not decided. |
| A08 Software or Data Integrity Failures | checked — no finding | outgoing messages validated against the AsyncAPI header and payload schemas before sending (`OutboxMessageMapper`), stable message ids, `traceparent` pattern-checked; incoming headers and payload schema-validated with format assertions; Jackson polymorphism closed by `answerType`, unknown fields ignored (tolerant reader by spec); CI `permissions: contents: read`, SHA-pinned actions; relay dead-letters only spec-breaking rows. |
| A09 Security Logging & Alerting Failures | finding SEC-06 (still open) | 13 × 401 and 25 × 403 (INSUFFICIENT_SCOPE, NOT_AUTHORIZED, NOT_REQUESTER) plus 9 dead-lettered messages produced no log line (every API1/API2/API5 entry and API10-03…11 read "log: (no line)"). Late Grandma answers are logged at INFO with message id and state only (good). Raw path text (A09-01/04) and lenient UUID text (A09-02) reach the MDC `helpRequestId` before conversion; today no line is written on those requests, so no leak — it becomes relevant with the SEC-06 fix. |
| A10 Mishandling of Exceptional Conditions | checked — no finding | cancel/accept/reject/answer on CANCELLED or CLOSED → 409 INVALID_STATE rule LIFECYCLE (A10-06…13); blank note → 422 MISSING_FIELD (A10-01); no body → 415; `{}`/unknown fields as cancellation → no note (A10-03); missing or unknown `version` → 400 (A10-04/05); 406 and 415 as Problem Details without internals; time-out poller timed out r8 after the clock moved 13 h with both providers TIMED_OUT and a suggestion (S06-01); an ANSWERED request is not timed out (S06-03, INV-09/D5). |

## Findings

```
SEC-14 · medium · API4 Unrestricted Resource Consumption
Location:  GET /help-requests, GET /help-requests/{helpRequestId}; cooking-assistance/.../adapter/out/persistence/JdbcHelpRequestQueries.java:220-242 (previousNotes, no limit),
           :195-205 (notes, no limit), :76-84 (list → readById per item); OpenAPI 1.0.11 HelpRequest.notes / previousNotes (arrays without maxItems)
Violates:  ADR0019 (bounded request and response sizes are the baseline's intent), ADR0016 L6 (keyset paging bounds items, not what each item carries); OWASP API4
Evidence:  as Lena, 20 × POST /help-requests on the same recipe/step, each cancelled with a 2000-char note (40 requests, 465 ms).
           Then as Tom, GET /help-requests/{20th} → 200, previousNotes=19, 43,755 bytes (API4-14);
           GET /help-requests?requester=<Lena>&limit=100 → 200, 21 items, 190 previousNotes, 464,539 bytes, 71 ms (API4-15).
           Every item of a page repeats the whole history of its step: for N requests on one step a page carries N(N−1)/2 notes of up to
           2 KB; at N=100 about 10 MB, and the 10th page of 1,000 requests about 190 MB for one GET. The history is built by one cook
           (unlimited requests, one note each) and fetched by any help:read holder. previousNotes is a join without an index on
           (requester, recipe, how_to_step). Rate limiting at the gateway slows the build-up and the fetching, it bounds neither.
Abuse-case test:
  Given Lena has cancelled 50 requests on step 4 of her Hollandaise, each with a 2000-char note
  When Tom requests GET /help-requests?requester=<Lena>&limit=100
  Then the response stays under a stated bound (e.g. previousNotes per item ≤ 10 most recent, or previousNotes left out of list items
       and paged on the single resource), and the query count per item is bounded
  -- B3-S03 US-CA-03 OWASP-API4
Owning step: 4 (spec: bound or page previousNotes), 11 (read model), 5 (ADR0016 L6 addition)
```

```
SEC-06 · medium · A09 Security Logging & Alerting Failures — still open (deferred from increment 0, due in increment 1)
Location:  cooking-assistance/.../adapter/in/rest/SecurityConfiguration.java:80-105 (entry point and access-denied handler: no log),
           ProblemDetailsAdvice.java:171-197 (handleDomainRejection, handleNotAuthorized, handleNotFound: no log),
           adapter/in/messaging/GrandmaHelpProvidedConsumer.java:48-53 (DEAD_LETTER without a line),
           adapter/in/rest/HelpRequestLogContext.java:27-33 (MDC from the raw path segment)
Violates:  ADR0019 "log security events without personal data", QS-14 (correlation id on every log line), deferral decision of 2026-11-01
Evidence:  run 1 produced 13 × 401 (API2-01…11, 13, API8-02) and 25 × 403 (API1-01…04, API5-01…14, API8-03) and 9 dead letters
           (API10-03…09, 11); the captured root logger has no line for any of them. The only lines in the whole run were the relay's
           schema-loader WARNs, the two INFO lines for ignored late answers, and container noise. An operator cannot see a token-guessing
           or object-probing campaign; QS-14's alert path has nothing to alert on.
           Related: the MDC key helpRequestId is set from the URI template variable before type conversion, so "[forged] [other]" and
           "1-1-1-1-1" (A09-01/02) would appear on any line logged in those requests once refusals are logged.
Abuse-case test:
  Given Tom's token and Lena's request
  When POST /help-requests/{id}/cancellation as Tom → 403 NOT_REQUESTER, and GET /help-requests/{id} without a token → 401
  Then exactly one WARN line per refusal is logged with code, operation, trace id and (for 403) the parsed help request id, and without
       the subject, title or body; a request whose path segment is not a UUID logs no raw path text
  -- B3-S03 US-CA-03 OWASP-A09:2025
Owning step: 9 (inbound adapters), 12 (log format, alert path); decision: close now or re-defer with a date
```

```
SEC-15 · low · API9 Improper Inventory Management (also API8: Host reflection, SEC-10 family)
Location:  Spring Security 7 OAuth2ProtectedResourceMetadataFilter, added by oauth2ResourceServer() in
           cooking-assistance/.../adapter/in/rest/SecurityConfiguration.java:57-62; larder-app (no server.forward-headers-strategy)
Violates:  OpenAPI 1.0.11 (no such path; every exposed operation is in the spec), ADR0019 addition of 2026-11-02 ("links … never the
           request's Host or forwarded headers", SEC-10), ADR0013 deny by default
Evidence:  GET /.well-known/oauth-protected-resource without a token → 200
           {"resource":"http://localhost:58106","bearer_methods_supported":["header"],"tls_client_certificate_bound_access_tokens":true} (WK-01);
           with "Host: evil.example" → 200 {"resource":"http://evil.example",…} (WK-02); X-Forwarded-Host ignored (WK-03);
           every 401 carries WWW-Authenticate: Bearer resource_metadata="http://<Host header>/.well-known/oauth-protected-resource" (WK-04, API8-02).
           The document names the wrong resource identifier (the spec's is https://larder.org/cooking-assistance, the token audience
           cooking-assistance), claims mTLS-bound tokens the service does not verify, and lets a caller choose the host in both places.
           Not exploitable against another user today (the reflection returns to the sender); behind the gateway the header names the
           internal host and scheme http.
Abuse-case test:
  Given no token
  When GET /.well-known/oauth-protected-resource (any Host)
  Then the response is 401 Problem Details (endpoint disabled) — or, if the metadata is wanted, 200 with resource = the configured
       public base URL and no claim the service does not enforce; the WWW-Authenticate challenge names no request-derived host
  -- B3-S03 US-CA-03 OWASP-API9
Owning step: 9 (SecurityConfiguration), 4 (spec, if the endpoint is kept), 5 (ADR0012 note)
```

```
SEC-16 · low · API8 Security Misconfiguration (review of the ADR0020 addition of 2026-11-05)
Location:  larder-app/src/main/java/org/larder/ProbeSecurity.java:21-23 (permitAll on /actuator/prometheus),
           larder-app/src/main/resources/application.yml (management on the API port, exposure health,prometheus)
Violates:  ADR0013 deny by default (an operation open to the network without a decision on who may read it); AP0008 fine-grained access
Evidence:  GET /actuator/prometheus without a token → 200, 136 metric families (API4-12), among them
           http_server_requests_seconds_count per method, uri template, status and outcome (a live map of which operations exist and
           how often they are refused), spring_security_authentications/authorizations and the filter-chain names,
           hikaricp_connections_max=10, jvm_info (runtime version), process_start_time/uptime, rabbitmq_* publish/ack counters,
           cooking_assistance_outbox_backlog and oldest age; ?includedNames filters work for anyone (API4-13). Each scrape runs two
           SQL statements (OutboxBacklogMetrics), cheap because of the partial index outbox_claim.
           The decision "same protection as the probes" holds only while the gateway does not route /actuator/** — nothing in the
           repository states or tests that rule, and the endpoint shares the API's port.
Abuse-case test (verification, no attacker):
  Given the deployment description
  When /actuator/prometheus is requested through the public entry point
  Then it is not reachable (gateway rule or management.server.port on an internal interface), while the scraper reaches it internally;
       the rule is written down where ADR0020 points
  -- B3-S06 US-CA-03 OWASP-API8
Owning step: 12 (host configuration), 5 (ADR0020: name the network rule)
```

```
SEC-17 · low · A07 Authentication Failures (planned-list item from the increment-0 re-test, due "ADR0012 inc 1")
Location:  cooking-assistance/.../adapter/in/rest/AcceptedTokens.java:23-33 (exp required, no upper bound; no iat check)
Violates:  ADR0012 ("every token expires" says nothing about how long a token may live; the planned decision is missing)
Evidence:  token signed with the run's key, exp = now + 3650 days → GET /help-requests/{id} 200 (API2-12). A stolen token of a
           misconfigured or compromised issuer stays valid for years; no revocation path exists (ADR0012 defines none).
Abuse-case test:
  Given a correctly signed token whose exp lies more than the agreed maximum (e.g. 24 h) after iat/now
  When any operation is called with it
  Then the response is 401 UNAUTHENTICATED
  -- B3-S03 US-CA-03 OWASP-A07:2025
Owning step: 5 (ADR0012: maximum lifetime, or an explicit accepted risk with the issuer's policy as the control), 9
```

```
SEC-18 · low · A06 Insecure Design (read-side visibility; API1/API3 by consequence)
Location:  cooking-assistance/.../application/HelpRequestQueries.java (no Actor in any query), JdbcHelpRequestQueries.previousNotes,
           adapter/in/rest/HelpRequestsController.java:60-66, 85-100; OpenAPI 1.0.11 HelpRequest.previousNotes, listHelpRequests
Violates:  INV-07 ("notes … shown on future requests" — to the cook), INV-08 ("providers see the note" — the cancellation note of that
           request), ADR0013 ("a chef's help is visible to the requester only" presupposes a caller the read side knows)
Evidence:  as Tom (cook, help:read), GET /help-requests/{Lena's} → 200 with notes and previousNotes (API1-09, API4-14: 19 earlier notes
           of Lena's other requests, each with its helpRequestLink); GET /help-requests?requester=<Lena> → Lena's whole list with every
           history (API1-12, API4-15); GET /helps?helpProvider=<Jonas> → everything Jonas ever answered (API1-13). Each read is allowed
           by the step-11 decision ("GET by id for any help:read"), but previousNotes is the requester's private history, not part of
           the request a provider needs to answer. The queries have no caller, so the chef rule of the spec (listHelps,
           getHelpOfHelpRequest: "only the original requester can see the help") cannot be enforced when chef helps arrive (increment 4).
Abuse-case test:
  Given Lena's earlier cancelled request on step 4 with the note "an ice cube saved it" and her new open request on step 4
  When Tom (not the requester) requests GET /help-requests/{new}
  Then previousNotes is absent (or the response is scoped to what a provider needs), while Lena's own GET shows it
  -- B3-S01 US-CA-03 OWASP-A06:2025
Owning step: 5 (ADR0013 addition: read rules per field/role), 4 (spec), 11 (queries take the actor)
```

## Tool triage
| Tool | Run | Result | Triage |
|---|---|---|---|
| OWASP Dependency-Check 13.0.0 | CI `security.yml`, PR #5 run 42501311822 (report downloaded to `ci-artifacts/dependency-check-report/`) | 66 dependencies; 1 vulnerability: `opentelemetry-api-1.62.0.jar` CVE-2026-54285, CVSS v3 5.3 MEDIUM, job green (threshold 7) | **false positive**: the CVE describes `@opentelemetry/core` (opentelemetry-js, `W3CBaggagePropagator.extract()` without size limits, fixed in 2.8.0); it is matched to the Java jar through the generic CPE `cpe:2.3:a:opentelemetry:opentelemetry`. The suppression file `tools/security/dependency-check-suppressions.xml` was removed in this increment, so the item will reappear on every run; a suppression with this reason is hygiene (no SEC id). |
| SpotBugs 4.10.5 + FindSecBugs 1.14.0 | same CI run, `spotbugsXml.xml` of both modules downloaded | 0 BugInstance in cooking-assistance and in larder-app | nothing to triage. |
| Schemathesis | CI `contract.yml`, run 42501311828 green | passed | not re-run; the schema-driven probes above (A05, API4, A10) agree with it. |
| gitleaks | CI `verify.yml` (pinned action) green on PR #5; not installed locally | — | manual grep over source, config, tooling and workflows: no credential; test-only passwords are literals of throw-away containers; `.gitleaksignore` holds one historical CI entry (c408cec) with a reason. No finding. |
| Error Prone 2.41.0 | every compile, ran in the copy build | clean | — |
| OWASP ZAP baseline | not set up (ADR0019 names it; planned since increment 0) | — | replaced by 209 hand-written probes; still a gap against ADR0019. |
| Hand probes | `SecurityProbeIT`, `SecurityProbe2IT` | ledgers `probe-results.txt`, `probe-results-2.txt` | every probe triaged above. |

## Earlier risks and deferrals
- Rate limiting at the AppShell/gateway (ADR0019): still valid as an assumption; SEC-14 shows a cost the gateway's rate limit does not bound (response size grows with data, not with request rate).
- **SEC-06 (security logging), deferred to increment 1 with QS-14: still open, re-confirmed** (38 refusals and 9 dead letters without a line). The increment added log context (trace id, help request id, INFO for ignored late answers) but no line for a refusal. Decision needed now: close in this increment or re-defer with a date; the raw-path MDC note above belongs to the same fix.
- SEC-11 (SHA-pinned actions, Boot patch level, security tools as CI jobs): actions pinned, Boot 4.1.2, SpotBugs/FindSecBugs and Dependency-Check as CI jobs → resolved except `pip install pyyaml==6.0.2` without a hash (hygiene) and the ZAP baseline (still not set up).
- `/actuator/prometheus` without authentication (ADR0020 addition): reviewed → SEC-16 (low). The decision can hold, but only with a written and verifiable network rule or a separate management port.
- Raw path text can reach the log: today no log line is written on such requests (A09-01/02/04 → 400/404 without a line), so not a finding by itself; folded into SEC-06 because the MDC value is taken before the UUID conversion.
- `POST /help-requests` has no help request id in the log context: observability only (the id exists only after the command); no security impact, no finding.
- Increment-0 observations: no maximum token lifetime → now SEC-17 (its planned date was this increment); "#" bind on the Grandma exchange → accepted risk in ADR0012, unchanged; queue argument migration → README note, unchanged; relay log flooding → `BrokerOutageIT` and the accepted ~40 lines/s during an outage (ADR0015 addition), unchanged; consumer retries without backoff → unchanged (only transient failures reach RETRY; schema and domain failures dead-letter at once, verified API10-03…11).
- SEC-01…05, SEC-07…10, SEC-12, SEC-13 (fixed in increment 0): regression-checked where the increment touched them — 413 on the new write operations (API4-07/08), scope gate on HEAD and all read paths (API5-10…15), `exp` required (API2-03), https-only pictures on both edges (A10-14, API10-07), `source` check (API10-03), OPTIONS/`/error` (API5-19, API9-01), links from the public base URL under a hostile Host (API8-15), non-URI/garbage inputs dead-lettered once (API10-08). All still fixed.

## Not tested
- OWASP ZAP (not set up, excluded by the package), gitleaks locally (not installed; CI result used), Dependency-Check locally (NVD download; CI report used).
- Production JWKS endpoint, TLS termination for HTTP and the gateway's routing of `/actuator/**` and `/.well-known/**` (not in the repository).
- The restricted broker user end to end in the probe run (the host ran as the container's admin user); `BrokerPermissionsIT` covers the ADR0012 permissions and was not re-run.
- Concurrency (double accept/reject races) beyond `ConcurrentAnswersIT`; the second instance of the time-out poller (QS-09 with two instances) — read in code only (`FOR UPDATE SKIP LOCKED`, savepoint per candidate).
- Oversized AMQP messages (a 100 MB body from a writer on the Grandma exchange would be read into memory by `AsyncApiMessageReader`): not sent, within the limits; the control is the broker ACL and `max_message_size` of the broker, both outside the code.
- Chef-help visibility rules (no chef help can exist in increment 1; see SEC-18 observation).
- Load, rate limiting, denial of service (excluded).

## Counts
critical 0 · high 0 · medium 2 (SEC-14, SEC-06 still open) · low 4 (SEC-15, SEC-16, SEC-17, SEC-18) · categories with verdict 20/20

## Probes run (reproducible)
Copy: `repo-copy/` (main 8ac22bb). Models: `tools/codegen/node/node generate-asyncapi-models.mjs <copy>/docs/cooking-assistance/api/cooking-assistance.asyncapi.yaml <copy>/cooking-assistance/target/generated-sources/asyncapi/org/larder/cookingassistance/adapter/messaging/generated org.larder.cookingassistance.adapter.messaging.generated` (run from the original's `tools/codegen`, read-only). Build and run: the Maven command in "Package and target" with `-Dit.test=SecurityProbeIT` (run 1) and `-Dit.test=SecurityProbe2IT` (run 2). Probe sources: `repo-copy/larder-app/src/test/java/org/larder/security/SecurityProbeIT.java`, `SecurityProbe2IT.java`. Ledgers: `probe-results.txt` (158 HTTP, 13 AMQP), `probe-results-2.txt` (51 HTTP). Totals: 209 HTTP probes, 13 AMQP publishes, two Testcontainers runs, about 2 h 10 min.
Read only (no probe): domain `HelpRequest` commands and guards, `TimeOutDueHelpRequests`/`TimeOutPolling`, `OutboxRelay`/`OutboxRetrySchedule`/`OutboxMessageMapper`/`AmqpMessagePublisher`, `Outbox`/`MicrometerCurrentTrace`, migrations V1–V6, `AmqpConsuming`, `ProbeSecurity`/`ProblemErrorReports`, `application.yml`, the three workflows, `schemathesis.toml`, poms.

## Decisions (user, 2026-11-08)

| Finding | Decision |
|---|---|
| SEC-06 medium | Fixed test-first (L1 to L6, L5b): one WARN line per refused caller and dead letter; status, code and rule on application and domain 403 lines; no subject (ADR0019); the rejection's type, not its text; UUID-only ids. A-4 of step 13 closed with it |
| SEC-14 medium | Fixed test-first (P1): `previousNotes` the 10 most recent, oldest first; INV-07, ADR0016, OpenAPI 1.0.12. The index and `notes` per request: planned list |
| SEC-15 low | Fixed test-first (W1, W2). Spring Security has no switch to turn the endpoint off, so it and the challenge name the configured public base URL. The decision is in ADR0020, not ADR0012 as the finding's owning step suggested |
| SEC-16 low | Fixed test-first (M1, M2): management on `LARDER_MANAGEMENT_PORT`, default 8081; ADR0020 |
| SEC-17 low | Risk accepted, ADR0012 addition, owner Annegret Junker |
| SEC-18 low | Deferred to increment 4 |

Reverse check of the fixes (Sonnet, read-only): no code defect, four wording drifts corrected (ADR0020, this section), one gap recorded (`notes`, ADR0016).

## Re-test (separate agent, Fable, 2026-11-08)

On a running instance (Testcontainers PostgreSQL 16, RabbitMQ 3.13); 161 HTTP probes (about 150 agreed, a parser bug in the agent's own probe forced a re-run of the fixtures) and 10 AMQP publishes.

| Finding | Verdict | Evidence |
|---|---|---|
| SEC-06 | fixed | every 401 and every 403 from each source one WARN line; 8 dead letters one WARN each; 422, 409, 404 and 400 none; no token, subject or note text; forged paths and ids never reach a line or the MDC |
| SEC-14 | fixed | the same abuse case: 10 notes per item, oldest first; a list page linear in its limit (about 2.1 MB at limit 100), no longer quadratic |
| SEC-15 | fixed | `resource` and every 401 challenge name the configured URL under a foreign Host and forwarded headers; token binding `false` |
| SEC-16 | fixed | the API port answers 404 for metrics and health; the management port serves only health and metrics, no module API |
| Regression sample | none | BOLA, BFLA, authentication bypasses and mass assignment refuse as before |

The earlier report's tampered-signature probe (API2-07) changed only the last base64url character, which can decode to identical bytes, so its method is unreliable; a mid-signature change is refused with 401.

New: **SEC-19 · low · A09**: the refusal lines logged the raw URI unbounded, so a sender could size the security log (no forging). Fixed test-first (L7): at most the path's first 200 characters. Not re-tested by the agent: the fix only shortens a log line; L7 covers the 401 and the 403 line.

Observations: O-3 the metadata filter also matches sub-paths, O-4 the metadata document and 401 lines also occur on the management port, O-5 the URL is not RFC 9728's path-insertion form → planned list. O-1 encoded control characters in a path are a 400 before the filter, O-2 Tomcat answers an HTTP/0.9 request line, O-6 firewall 400s on the management port are HTML, O-7 404 against 401 tells an exposed actuator id from another → noted, no action.

Not tested: the platform's rule keeping the management port off the public route, gateway routing of `/.well-known/**`, the consumer's retry path, HTTP/2, load.

## Final gates (2026-11-08)

`./mvnw -o clean verify`: 332 + 137, 14 + 42; Spotless, Error Prone, Spectral. Fault injection (exact `quality.yml` command, offline, before L7): 3 green, E5b 1.2 s. Intent coverage: exit 0, no gaps; INV-03 without a domain-level test.
