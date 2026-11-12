# Security report — Cooking Assistance increment 2 (Board B2, US-CA-02 slices B2-S01, B2-S02; B3-S03…S06 extended), reviewer Claude Fable 5.1

## Package and target
Received: `package.md`; the repository copy `repo-copy/` at main + step 13 = e1c840a (increment diff `git diff 8bbc221 e1c840a`, 109 files, read without ledger and journals); OpenAPI 1.0.15, AsyncAPI 1.0.11, `CHANGELOG.md`; ADR0012, 0013, 0015, 0016, 0019, 0020, 0021 with their increment-2 additions; `invariants.md` B2 section (INV-10…17), quality scenarios; `reviews/increment-1-security.md` (SEC-01…SEC-19, decisions, re-test) and `reviews/increment-2-review.md` (deferred A-3, A-5, A-6); test identities `TestTokens`/`TestSigningKeys`/`HelpRequests`; CI results of PR #8 in `ci-artifacts/` (Dependency-Check 13.0.0 HTML+JSON, SpotBugs 4.10.5 + FindSecBugs 1.14.0 XML of both modules, Schemathesis JUnit report and service log). Not read: ledger, journals, conversation history.

Target: larder-app in the copy, started by three probe ITs (`repo-copy/larder-app/src/test/java/org/larder/security/SecurityProbeInc2IT.java`, runs 1 and 2; `SecurityProbeInc2bIT.java`, run 3; `@SpringBootTest` RANDOM_PORT) on Testcontainers PostgreSQL 16-alpine and RabbitMQ 3.13 with the `grandma-avatar` exchange pre-declared as its owner would (`TestBroker`), outbox relay and time-out poller on (defaults), a Logback `ListAppender` on the root logger capturing every `org.larder` line per probe. AsyncAPI models generated into the copy with the original's pinned toolchain (`tools/codegen/node/node generate-asyncapi-models.mjs`, read-only use of the original). Build: `./mvnw -o -B -pl cooking-assistance,larder-app -Dspotless.check.skip=true -Dskip.installnodenpm -Dskip.npm -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=<IT> -Dfailsafe.failIfNoSpecifiedTests=false verify`, one build at a time, offline. Evidence ledgers next to this report: `probe-results-run1.txt` (31 HTTP, 0 AMQP; stopped at B-01 by a probe bug — `helpLink` instead of `helpId`), `probe-results-run2.txt` (128 HTTP, 15 AMQP), `probe-results-run3.txt` (20 HTTP, 0 AMQP); build logs `probe-run-1.log`, `probe-run-2.log`, `probe-run-3.log`; the diffs `diff-main.patch`, `diff-tooling.patch`, `diff-docs.patch`.

Limits applied: 179 HTTP probes of ≤ 250 and 15 AMQP publishes of ≤ 20; no load or denial-of-service run (the largest repeated set: 12 requests + 12 cancellations for the SEC-14 regression; the largest body 1 MB + 11 B; the largest single read 211 KB, G-06). Nothing outside local containers was touched. The original repository was read only; `git status --short` there shows the pre-existing untracked `docs/cooking-assistance/ledger/wip/` and ` M docs/cooking-assistance/implementation-log.md` — the latter is the moderator's step-14 ledger line ("Step 14 input (inc 2, 2026-11-12, user)…", one insertion), not a change of this review.

Identities used: Lena (cook, requester), Tom (another cook, free plan), Jonas and Mia (cook + community), Marco (chef), Jonas with the community role only, read-only (`help:read`) and write-only (`help:write`) scopes, no token, expired token, wrong audience; the Grandma Avatar context on the broker (`source: grandma-avatar`), and a sender with `source: community`.

## Category verdicts
| Category | Verdict | Reason / evidence (probe ids in the ledgers; run 2 unless marked r1/r3) |
|---|---|---|
| API1 Broken Object Level Authorization | checked — no finding | Tom cancels/accepts/rejects on Lena's ingredient request → 403 NOT_REQUESTER (B-30…32); Jonas accepts his own help → 403 (B-33); Jonas answers his own request → 403 OWN_REQUEST INV-36 (B-27); Jonas answers a request that asked Grandma only → 403 PROVIDER_NOT_ASKED INV-01 (B-29); a help under the wrong request → 409 LIFECYCLE (B-34). `previousNotes` by ingredient name stays per requester: Tom's "saffron" request after Lena's cancelled "saffron" shows no note (C-05/C-06b, `previousNotes` absent); the SQL joins `earlier.requester = read.requester` (`JdbcHelpRequestQueries.java:73`). Reads by any `help:read` holder are the step-11 decision; their breadth is SEC-18 (see A06). |
| API2 Broken Authentication | checked — no finding | no token, expired, wrong audience on the new request type → 401 UNAUTHENTICATED with one WARN each (A-06…08); read-only scope creating → 403 INSUFFICIENT_SCOPE, write-only scope reading → 403 (A-09/10). `SecurityConfiguration`/`AcceptedTokens` are not in the increment diff; SEC-17 (lifetime) stays the accepted risk. |
| API3 Broken Object Property Level Authorization | checked — no finding | create with `status`, `requester` = Tom, `helpRequestId`, `version` → 201, stored requester = Lena, status OPEN (A-11…11c); help with `isAccepted: true`, `helpProvider` = Mia, `helpId`, `rejected` → stored provider = Jonas, `isAccepted` false (B-10…10c); the response key sets of an ingredient HelpRequest and of a Substitutes Help are subsets of the spec's schemas (A-20b, B-10d); a step request carries no ingredient fields (A-22b); `howToStep`/`pictures` on an ingredient request → 400 by the spec's `if/then` (A-12/13); an answer of the other kind → 422 ANSWER_DOES_NOT_MATCH_REQUEST INV-37 both ways (B-08/09). |
| API4 Unrestricted Resource Consumption | **finding SEC-20** | the edge limits hold: `ingredientName` > 200, `substituteRequirements` > 2000, 21 substitutes, 11 ingredients, name > 200, 1 MB + 11 B → 400/413 (A-14/15/27, B-16/17/18/25); `value` ≤ 0, as string, 1200 digits, unknown unit → 400 (B-11…14, B-22/23). But `value` has no maximum in either spec: `1e1500` is accepted and bricks the request and every list containing the help (SEC-20, r3 F-02…F-11); `1e999` turns a 10.7 KB request into a 210 KB help on every read (G-04/05); `1e200000` → 500 at write (B-21, rolled back). SEC-14 regression on the new name match: 13 "bacon" requests with 2000-char notes → `previousNotes` 10, 22 KB (C-15b); Lena's list of 22 items 202 KB, linear (C-16b). |
| API5 Broken Function Level Authorization | checked — no finding | asking a CHEF for an ingredient → 400 by the spec's `not: contains CHEF` (INV-10 at the edge; the aggregate guard `ensureProvidersMayBeAsked` behind it) (A-02); community-only token and chef creating → 403 NOT_AUTHORIZED (A-04/05); Jonas as GRANDMA_AVATAR, Tom without the community role, Marco as COMMUNITY and as CHEF answering → 403 (B-02…05); the type filters are open to every `help:read` holder by decision (A-23, A-26). |
| API6 Unrestricted Access to Sensitive Business Flows | checked — no finding | the reject → propose-again loop: a rejected substitute is refused for every provider, whatever the case, spaces or amount (B-35…37, 422 SUBSTITUTE_REJECTED_BEFORE INV-15); a mix with the rejected name is new by S3 (B-38); Grandma's repeat is acknowledged and ignored, no new help, one INFO line (D-10…10c). Observation O-1: the name equality is trivially evaded with a zero-width space or NBSP (B-39/40 → 201) — a UX rule, not a protection; no finding. Volume protection stays the gateway's (ADR0019, accepted). |
| API7 Server Side Request Forgery | not applicable | increment 2 adds no outbound call; `ingredientName`, `substituteRequirements` and substitute names are stored text, never resolved; the "known ingredient" reference is on the planned list (invariants, open question). |
| API8 Security Misconfiguration | checked — no finding | the 500s of SEC-20 answer the spec's Problem (`INTERNAL_ERROR`, no detail, no stack) (B-21, r3 F-03…F-11); 400/422 details repeat only the spec's own messages (A-02, A-12, B-22); headers, CORS, management port and error valve are unchanged since the increment-1 re-test (not in the diff; not re-probed). The step 12 CI services (`load.yml`) run throw-away credentials on job-local containers. |
| API9 Improper Inventory Management | checked — no finding | the increment adds no path or operation; the new type is served by the existing operations; `type` outside the enum → 400 (A-24), an unbuilt type in the filter → 200 empty (A-25); Schemathesis ran the 11 operations of 1.0.15 (CI junit). Observation O-5: Schemathesis skipped `GET /help-requests` and `GET /helps` ("told to run no test cases"), so the type filters got no generated case in CI. |
| API10 Unsafe Consumption of APIs | **finding SEC-22** | valid substitutes → ANSWERED, one help (D-01); redelivered id → no second help (D-02); blank name → DomainRejection, value 0 and string → InvalidMessage, other ingredient, substitutes on a step request, Grandma not asked, `source: community`, 21 substitutes, name > 200 → dead-lettered at once, `x-death.count=1`, one WARN with id and type (D-03/04/05/07/08/09/12/13/15); repeated rejected substitute and an answer to a CLOSED request → acknowledged and ignored, INFO (D-10, D-14). But a spec-valid `value: 1e400` becomes `Infinity` in the generated `double`, `BigDecimal.valueOf` throws `NumberFormatException`, and the consumer retries it to the delivery limit (D-06: 5 WARN stack traces, then DLQ) — SEC-22. |
| A01 Broken Access Control | checked — no finding | by reference: API1, API3, API5 clean; the read breadth is SEC-18 (A06), widened by this increment. |
| A02 Security Misconfiguration | checked — no finding | API8 by reference; still no container or deployment manifest in the repository; `load.yml`/`compatibility.yml` run with `permissions: contents: read`, on `pull_request` (not `pull_request_target`), SHA-pinned actions, `python3 -I`, oasdiff with a verified SHA-256. |
| A03 Software Supply Chain Failures | **finding SEC-23** (low) | Dependency-Check 13.0.0 (CI): 66 dependencies, 1 MEDIUM → false positive (triage); SpotBugs + FindSecBugs: 0 instances in both modules; Error Prone clean in the three copy builds; actions pinned; JaCoCo 0.8.13, Gatling 3.16.0, gatling-maven-plugin 4.21.12 pinned. But the new `load-tests` module is outside the `security` profile's scan, and the new CI jobs install `pyjwt`, `cryptography`, `pyyaml` and run `npx @asyncapi/cli` without hashes or a lock (A-5, SEC-11 rest) — SEC-23. |
| A04 Cryptographic Failures | checked — no finding | no credential in the increment diff (grep over code, workflows, tooling); gitleaks green on PR #8; `.gitleaksignore` unchanged (one historical entry); the CI identities are generated per run (`test_identity.py`) and the service containers' passwords are job-local literals; nothing new is stored encrypted or hashed. |
| A05 Injection | checked — no finding | `ingredientName` `x' OR '1'='1' -- ; drop table help_request;` and `substituteRequirements` with `${jndi:…}`/`<script>` → stored and returned verbatim, JSON-encoded, no log line (A-19…19c); `type` filter with SQL text → 400 (A-24); a name `%` matches no history (C-10/11b); the new SQL (`lower(trim(…))` match, V9 index, type filters) is static text with bound parameters (`JdbcHelpRequestQueries.java:60-78, 121-134, 181-193`); U+0000 → 400 (A-28). |
| A06 Insecure Design | finding SEC-20 (design cause); SEC-18 widened | the contract bounds every text and array of a substitute but not the amount: `SubstituteIngredient.value` is `number, exclusiveMinimum 0` in both specs and INV-12 asks only for "a positive amount" — the design gap behind SEC-20/21/22. SEC-18 (reads without an actor) is unchanged in code and wider in data: `ingredientName`, `substituteRequirements` and the cook's notes from *other recipes* (INV-15) now reach every `help:read` holder (C-07b: Tom reads Lena's saffron note carried over from the risotto; B-45/46). Deferred to increment 4 as decided; say so there. |
| A07 Authentication Failures | checked — no finding | SEC-17 accepted (ADR0012 addition); no token code changed in the increment. |
| A08 Software or Data Integrity Failures | **finding SEC-21** | outgoing messages are still validated before sending (`OutboxMessageMapper`), incoming ones schema-validated with formats (D-04/05/13/15); Jackson polymorphism closed by `answerType`. But the HelpProvided event of a stored substitutes help with `value` above `Double.MAX_VALUE` (`1e400`, B-20) is built with `doubleValue()` → `Infinity` → written as the string `"Infinity"` → refused by the relay's own schema check and dead-lettered: the help exists and is accepted/rejected over REST, consumers never learn of it (E-01, B-28 log line, r3 H-01: three dead-lettered HelpProvided rows) — SEC-21. The exact amount also loses precision for every consumer (a `double`) while the REST reader gets the `BigDecimal`. |
| A09 Security Logging & Alerting Failures | checked — no finding | every 401/403 of the new operations writes one WARN with code (and rule) and no subject or text (A-04…10, B-02…05, B-27…33); 422/409/400 write nothing; dead letters one WARN with message id and the rejection's type (D-03…15); ignored answers one INFO with id and reason type/state; of 62 captured `org.larder` lines none holds a name, note, requirement, injection string or subject (E-04 = 0); the 500 lines carry the exception class and, for B-21, Spring's SQL text with `?` placeholders, never values. Observation: SEC-22's retry path logs five stack traces per message. |
| A10 Mishandling of Exceptional Conditions | finding SEC-20 (by reference) | blank name/requirements → 422 MISSING_FIELD INV-11 (A-16/17); blank substitute name → 422 VALIDATION_FAILED INV-12 (B-19, A-3 known); answer/reject after CLOSED → 409 LIFECYCLE (B-43/44); `1e200000` → 500 but rolled back, nothing stored (B-21, outbox rows E-01). But a stored amount whose plain form exceeds 1000 digits makes every later read *and command* of the request fail with 500 (`AnswerJsonMapper.toAnswer` → `IllegalStateException("Cannot read the stored answer")`), with no path to clear it — SEC-20. |

## Findings

```
SEC-20 · high · API4 Unrestricted Resource Consumption (also A10 Mishandling of Exceptional Conditions, A06 Insecure Design)
Location:  POST /help-requests/{helpRequestId}/helps with a Substitutes answer;
           cooking-assistance/src/main/java/org/larder/cookingassistance/domain/helprequest/SubstituteIngredient.java:14 (hasAmount: signum > 0 only),
           adapter/out/persistence/AnswerJsonMapper.java:28-36 (BigDecimal written as "1E+1500" into jsonb, which PostgreSQL stores as numeric and renders in plain digits),
           AnswerJsonMapper.java:64-80 (readValue with Jackson's default StreamReadConstraints, maxNumberLength 1000 → "Cannot read the stored answer"),
           adapter/out/persistence/HelpRequestMapper.toDomain (every command reloads every help through it), JdbcHelpRequestQueries.helpView:196-216;
           OpenAPI 1.0.15 and AsyncAPI 1.0.11 SubstituteIngredient.value: number, exclusiveMinimum 0, no maximum; invariants.md INV-12 ("a positive amount")
Violates:  ADR0019 (bounded request and response sizes), ADR0016 (a page bounded by its limit), INV-12 (an amount a cook can use), ADR0013 deny by default
           for an input the design never bounded; OWASP API4
Evidence:  run 3. As Jonas (community), POST …/helps with substituteIngredients [{"name":"n0-0","value":1e1500,"unit":"GRAM"}] (12-byte literal) → 201 (F-02);
           the row help.answer::text is 1734 chars (F-02b). Afterwards, as Lena: GET the help → 500 INTERNAL_ERROR (F-03); GET the request's helps → 500 (F-04);
           as Tom: GET /helps?limit=100 → 500 for every caller whose page holds it (F-05, G-07); as Jonas: GET /helps?helpProvider=<Jonas> → 500 (F-06);
           as Mia: a second answer → 500 (F-08); as Lena: reject → 500, accept → 500, cancel → 500 (F-09…F-11). The request stays ANSWERED (F-12, H-02b);
           no operation can remove the help, so the cook's request and the provider lists are unusable until the row is edited by hand.
           Each failure logs "unexpected error <IllegalStateException: Cannot read the stored answer>". Below the 1000-digit line the same input amplifies:
           20 substitutes × 10 ingredients × 1e999 (10,667-byte request, G-04a) → a 209,894-byte help on every read (G-05), 211 KB per helps page (G-06);
           a /helps page of 100 such helps would be about 21 MB. Above the database's limit, 1e200000 → 500 at write, rolled back (run 2, B-21).
           Over the broker the same amount never reaches storage (SEC-22), so only REST providers can plant it.
Abuse-case test:
  Given Lena's ingredient request that asked the Community, and Jonas's help on it with value 200 GRAM
  When Jonas proposes a substitute whose amount is 1e1500 (and, as a second case, 1e999 twenty times ten)
  Then the response is 400 VALIDATION_FAILED from the spec's maximum (or 422 from INV-12's bound), nothing is stored,
       and afterwards Lena's GET …/helps, Tom's GET /helps?limit=100 and Lena's acceptance of the 200 GRAM help all succeed
  -- B2-S02 US-CA-02 OWASP-API4
  Given a help whose stored answer cannot be read (a row written before the bound existed)
  When Tom requests GET /helps?limit=100
  Then the page is served with that help marked unreadable or left out, never a 500 for the whole page
  -- B2-S02 US-CA-02 OWASP-A10:2025
Owning step: 4 (both specs: maximum and, if wanted, a precision rule for value; CHANGELOG), 3/6 (INV-12: an upper bound in the aggregate),
             7 (AnswerJsonMapper: read limits or plain-string storage), 11 (helpView tolerant of one bad row); the ADR0019 baseline could name "every number is bounded"
```

```
SEC-21 · medium · A08 Software or Data Integrity Failures (also A06)
Location:  cooking-assistance/src/main/java/org/larder/cookingassistance/adapter/out/persistence/AnswerMessageMapper.java:49-55
           (ingredient.value().doubleValue() into the generated double field), adapter/out/messaging/OutboxRelay.java:82-87 (dead-letters the row);
           AsyncAPI 1.0.11 SubstituteIngredient.value (number without maximum), generated SubstituteIngredient(String name, double value, …)
Violates:  ADR0015 ("the message keeps its promise to consumers or is never sent" — here the help is kept and the promise is not), ADR0015 L5 (dead letters are
           for rows that break the spec; a spec-valid command must not produce one), AsyncAPI HelpProvided (every provided help is published)
Evidence:  run 2. As Mia, POST …/helps with value 1e400 → 201 (B-20); the help is readable with its exact amount (B-20b, 401 digits). The relay then logs
           "outbox row 7 (HelpProvided) dead-lettered: … /answer/substitute/0/substituteIngredients/0/value: string found, number expected" (B-28 log, E-06);
           the outbox holds HelpProvided published=false dead_lettered_at set, last_error as above (E-01, E-02 total 1). Run 3 repeats it for 1e1500 and
           1e999 × 200 (H-01: three HelpProvided rows dead-lettered). Every other context (Grandma Avatar, notifications) never sees the help; the cook can
           still accept it and the HelpAccepted event names a help nobody received. Amounts below Double.MAX also lose precision on the way out
           (BigDecimal → double), so consumers and REST readers disagree on the number the cook saw.
Abuse-case test:
  Given Lena's ingredient request and the probe queue on cooking-assistance.help.provided
  When Jonas proposes a substitute with value 1e400 (or any amount the spec allows)
  Then either the request is refused by the spec's maximum, or one HelpProvided message with the exact amount is received within QS-08's bound and
       no outbox row of the request is dead-lettered
  -- B2-S02 US-CA-02 OWASP-A08:2025
Owning step: 4 (AsyncAPI: maximum; decide the number format of value), 10 (AnswerMessageMapper: no lossy conversion, or refuse at the command), 5 (ADR0015 note)
```

```
SEC-22 · low · API10 Unsafe Consumption of APIs (also A10)
Location:  cooking-assistance/src/main/java/org/larder/cookingassistance/adapter/in/messaging/GrandmaAnswerMapper.java:86-92
           (BigDecimal.valueOf(ingredient.value()) on a generated double), GrandmaHelpProvidedConsumer.java:66-69 (RuntimeException → RETRY),
           AmqpConsuming.java DELIVERY_LIMIT 4; AsyncAPI 1.0.11 SubstituteIngredient.value without maximum
Violates:  ADR0015 ("a message that can never succeed goes to the dead-letter queue" at once; retries are for transient failures), AsyncAPI consumeGrandmaHelpProvided
Evidence:  run 2, D-06: a schema-valid answer with value 1e400 (Jackson parses it to Infinity in the generated double) → "Grandma Avatar answer not processed,
           retrying <NumberFormatException: Character I is neither a decimal digit number…>" five times with a stack trace each, then the queue's delivery
           limit dead-letters it (DLQ x-death.count=1 after about 4 s); no help stored (D-06c helps=1 from D-01). A writer on the Grandma exchange (a trusted
           context by ADR0012) can make the consumer spend five deliveries and five stack traces per message; the content never reaches the aggregate.
Abuse-case test:
  Given Lena's ingredient request that asked the Grandma Avatar
  When the Grandma Avatar's answer carries value 1e400
  Then the message is dead-lettered on its first delivery with one WARN line naming the message id and InvalidMessage (or refused by the spec's maximum
       before it is read), and the request stays OPEN
  -- B2-S02 US-CA-02 OWASP-API10
Owning step: 9 (map NumberFormatException/NaN/Infinity to InvalidMessage; or read value as BigDecimal), 4 (AsyncAPI maximum)
```

```
SEC-23 · low · A03 Software Supply Chain Failures (CI and test tooling of step 12)
Location:  pom.xml profiles `load` (adds load-tests) and `security` (Dependency-Check `aggregate`, SpotBugs) — .github/workflows/security.yml runs `-Psecurity` only;
           load-tests/pom.xml (gatling-charts-highcharts 3.16.0, amqp-client, gatling-maven-plugin 4.21.12);
           .github/workflows/load.yml:155 (`pip install pyjwt==2.10.1 cryptography==45.0.7`, no hashes), compatibility.yml:36 (`pip install pyyaml==6.0.2`),
           tools/contract/spec-compatibility.sh:32 (`npx --yes @asyncapi/cli@6.2.2`, no lock or integrity — step 13 A-5)
Violates:  ADR0019 (Dependency-Check as the review tool for the dependencies the project runs), SEC-11 decision (pinned, auditable tooling)
Evidence:  `ci-artifacts/run-43178511533/dependency-check-report.json` lists 66 dependencies, none of the Gatling tree (not in the reactor without -Pload);
           spotbugsXml.xml exists only for cooking-assistance and larder-app. The load job builds the service, runs it with job-local credentials and executes
           the Gatling simulation with whatever the unpinned transitive tree resolves to on the runner; the job has `contents: read` and no repository secret,
           so the exposure is the runner and the result's trust, not the product. Versions are pinned by number, not by hash; the actions are SHA-pinned.
Abuse-case test (verification, no attacker):
  Given the security workflow
  When Dependency-Check and SpotBugs run
  Then load-tests is in the reactor they scan (e.g. `-Psecurity,load`), and the pip and npx installs of load.yml, compatibility.yml and verify.yml are
       pinned with hashes or a lock file
  -- B2-S02 US-CA-02 OWASP-A03:2025
Owning step: 12 (workflows, poms); closes the rest of SEC-11 and A-5 together
```

## Tool triage
| Tool | Run | Result | Triage |
|---|---|---|---|
| OWASP Dependency-Check 13.0.0 | CI `security.yml`, PR #8 run 43178511533 (`ci-artifacts/run-43178511533/dependency-check-report/`) | 66 dependencies; 1 vulnerability: `opentelemetry-api-1.62.0.jar` CVE-2026-54285, CVSS 5.3 MEDIUM; job green (threshold 7) | **false positive**, as in increment 1: the CVE is `@opentelemetry/core` of opentelemetry-js (`W3CBaggagePropagator.extract()` without size limits, fixed in 2.8.0), matched through the generic CPE `opentelemetry:opentelemetry`; the suppression file is still absent, so it reappears each run (hygiene, no id). Coverage gap: `load-tests` not scanned → SEC-23. |
| SpotBugs 4.10.5 + FindSecBugs 1.14.0 | same CI run, `spotbugsXml.xml` of both modules | 0 BugInstance in cooking-assistance and larder-app (effort Max, threshold Medium) | nothing to triage; `load-tests` not analysed (SEC-23). |
| Schemathesis | CI `contract.yml`, run 43178511234 (junit, service log) | 11 test cases, 0 failures, 0 errors; `GET /help-requests` and `GET /helps` skipped ("told to run no test cases"); stateful run 301 s; the service log holds 403 NOT_AUTHORIZED WARN lines and returned-message WARNs (no consumer queues in CI), no 5xx | **confirmed nothing**; observation O-5: the two list operations, whose `type` filters this increment added, received no generated case; `provideHelp` with `value` was generated within Hypothesis defaults and never reached the 1000-digit line, so SEC-20 is outside what the run could see. |
| gitleaks | CI `verify.yml` (pinned action) green on PR #8; not installed locally | — | manual grep over the increment diff: only job-local container passwords (`contract`) and generated run identities; no finding. |
| Error Prone 2.41.0 | every compile of the three copy builds | clean | — |
| OWASP ZAP baseline | not set up (ADR0019 names it; planned since increment 0) | — | replaced by 179 hand-written probes; still a gap against ADR0019 (unchanged). |
| Hand probes | `SecurityProbeInc2IT` (runs 1, 2), `SecurityProbeInc2bIT` (run 3) | ledgers `probe-results-run1.txt`, `-run2.txt`, `-run3.txt` | every probe triaged above. |

## Earlier risks and deferrals
- Rate limiting at the gateway (ADR0019): still valid as an assumption; SEC-20 and SEC-21 do not depend on request rate (one request suffices).
- SEC-11 rest (pip hashes, ZAP) and step 13 A-5 (`npx` without lock): still open and extended by `load.yml`'s `pyjwt`/`cryptography` installs → folded into SEC-23.
- SEC-14 (bounded `previousNotes`): fix holds on the new ingredient-name match (C-15b: 10 notes, 22 KB; C-16b: a 22-item page of 202 KB, linear). The per-Cook history scan (step 13 A-6): unchanged, deferred with SEC-18; V9 (`help_request (requester, id)`) narrows it to one cook's rows, as ADR0016 says.
- SEC-15, SEC-16 (metadata endpoint, management port): code unchanged in the increment; not re-probed. SEC-17 (token lifetime): accepted risk, unchanged.
- SEC-18 (reads without an actor): deferred to increment 4 as decided; **increment 2 widens it** — `ingredientName`, `substituteRequirements`, the cook's cross-recipe notes (INV-15) and every substitutes help now reach every `help:read` holder (C-07b, B-45/46). No new id; the increment-4 decision should cover the new fields.
- SEC-19 (bounded URI in refusal lines): holds (A-10 line shows the path only).
- SEC-06 (security logging): fixed; regression clean on the new operations (A-04…A-10, B-02…B-05, B-27…B-33, D-03…D-15: one line per refusal or dead letter, none without).
- Step 13 A-3 (`provideHelp` 422 VALIDATION_FAILED undocumented): confirmed (B-19, blank substitute name → 422 VALIDATION_FAILED INV-12); deferred as decided; the amount rules never reach that 422 over REST (0, negative, string and 1e-20000 all stop at 400).
- Increment-0/1 observations ("#" bind on the Grandma exchange, listener reconnect noise, consumer retries without backoff): unchanged; the last one is what SEC-22 exercises.

## Observations (no id)
- O-1 INV-15's "same substitute" is name equality after `strip().toLowerCase()`: `quark​` and `quark ` pass as new (B-39/40 → 201), as would "quark." — the rule is a courtesy to the cook, who can reject again; the SQL match for `previousNotes` uses `lower(trim())`, a slightly different normalisation (`trim` strips ASCII space only). No security impact; worth one line in INV-15 if the team cares.
- O-2 `1e200000` ends in a 500 with Spring's `DataIntegrityViolationException` (PostgreSQL "value overflows numeric format"), rolled back — fail-closed, but a 5xx for a spec-valid body; the spec maximum of SEC-20 removes it.
- O-3 Run 2's ledger line D-06b re-captured the previous 60 log lines, so E-05's count (62) and the duplicated E-06 lines are a ledger artefact, not duplicate logging by the service.
- O-4 The HelpRequestCreated event now carries `ingredientName` and `substituteRequirements` (free text of up to 200 + 2000 chars) to every consumer of the exchange — by spec (AsyncAPI 1.0.10); no finding, but the same breadth as SEC-18 on the message side.
- O-5 Schemathesis skipped both list operations (see triage); the `--seed`/`-n 25` run cannot reach the number sizes of SEC-20 — a schema `maximum` would also make the generator honour the bound.
- O-6 `GrandmaAvatar.java` (load harness) defaults to `guest/guest` and the load job passes the broker password on the Maven command line: job-local, throw-away values; fine for CI, never for a shared broker.

## Not tested
- The Grandma Avatar sending `1e1500` or `1e999 × 200`: by code the same `double` path as D-06 (Infinity → NumberFormatException) or a stored `double` whose plain form never exceeds about 310 digits, so SEC-20's unreadable row cannot be planted from the broker (read in code, not probed, to stay within the AMQP budget).
- The time-out poller with an unreadable help: not reachable — a request with a help is ANSWERED and `claimDueIds` claims OPEN rows only; a rejection would make it OPEN, but the rejection itself fails (F-09). Read in code (`TimeOutDueHelpRequests.java:43`, `JdbcHelpRequestRepository.findDueForTimeOut`).
- OWASP ZAP (not set up), gitleaks and Dependency-Check locally (CI results used), the restricted broker user end to end (`BrokerPermissionsIT` not re-run), the production JWKS endpoint, TLS termination, the gateway's routing, security headers and the management port (unchanged code, increment-1 re-test stands), concurrency beyond `ConcurrentAnswersIT`, load (excluded).

## Counts
critical 0 · high 1 (SEC-20) · medium 1 (SEC-21) · low 2 (SEC-22, SEC-23) · categories with verdict 20/20

## Probes run (reproducible)
Copy: `repo-copy/` (main + step 13, e1c840a). Models: `tools/codegen/node/node generate-asyncapi-models.mjs <copy>/docs/cooking-assistance/api/cooking-assistance.asyncapi.yaml <copy>/cooking-assistance/target/generated-sources/asyncapi/org/larder/cookingassistance/adapter/messaging/generated org.larder.cookingassistance.adapter.messaging.generated`, run from the original's `tools/codegen` (read-only). Build and run: the Maven command in "Package and target" with `-Dit.test=SecurityProbeInc2IT` (runs 1 and 2) and `-Dit.test=SecurityProbeInc2bIT` (run 3). Totals: 179 HTTP probes (31 + 128 + 20), 15 AMQP publishes (0 + 15 + 0), three Testcontainers runs, about 25 min of wall time.
Read only (no probe): `HelpRequest` guards for substitutes (`ensureSomethingProposed`, `ensureSubstitutesMatchRequest`, `ensureNotRejectedBefore`, `answerTimeOf`), `RequestHelpWithIngredient`, `ProvideHelp.admitCommunityAnswer`, `GrandmaHelpProvidedConsumer`/`Listener`, `AsyncApiMessageReader`, `OutboxRelay`/`OutboxMessageMapper`/`Outbox`/`OutboxEventMapper`/`AnswerMessageMapper`, `AnswerJsonMapper`, `JdbcHelpRequestQueries`, `JdbcHelpRequestRepository`, `TimeOutDueHelpRequests`, migrations V7–V9, `ProblemDetailsAdvice`, `OpenApiRequestValidation`, `SecurityConfiguration` (unchanged), `application.yml`, the four workflows, `load-tests` sources and pom, `seed-help-requests.sql`, `spec-compatibility.sh`, `asyncapi_as_openapi.py`, `intent_coverage.py`, `schemathesis.toml`.

## Decisions (user, 2026-11-12)
| Finding | Decision |
|---|---|
| SEC-20 high | fix: an amount is at most 10,000 with at most 3 decimal places — step 3 r19 (INV-12), step 4 r23 (OpenAPI 1.0.16, AsyncAPI 1.0.12: `maximum: 10000`), step 6 domain guard; the spec bound is breaking for QS-15, so the gate gets a named, path-scoped exception (ADR0014 addition, `tools/contract/oasdiff-err-ignore.txt`; QS-15 note in quality-scenarios.md) |
| SEC-21 medium | fix through SEC-20's bound (an amount within it always fits a `double` exactly); the BigDecimal → double conversion stays as accepted in step 7 |
| SEC-22 low | fix through the AsyncAPI maximum: the message is schema-invalid and dead-lettered at once |
| SEC-23 low | deferred with SEC-11 and step 13 A-5 (hashes and locks; load-tests in the security scan) |
| SEC-18 | still deferred to increment 4; its widening by increment 2 recorded |
| O-1, O-5 | planned list (INV-15 normalisation with trim vs strip; Schemathesis on the list operations) |
| SEC-20 second abuse case (a read side that serves a page around an unreadable row) | planned list: no write path can plant such a row any more |

## Fixes (Red first, by owning step)
- REST (step 9): an amount above the maximum (10000.001, 1e400, 1e1500) → 400 VALIDATION_FAILED, no Help — Red 201, Green OpenAPI 1.0.16 (`-- B2-S02 US-CA-02 INV-12 SEC-20 SEC-21 OWASP-API4 OWASP-A08`); an amount with 4 decimal places → 422 VALIDATION_FAILED INV-12 (green on activation, trial without the decimal rule → 201).
- Broker (step 9): Grandma's 10000.001 and 1e400 → DEAD_LETTER at once — Red ACK and RETRY, Green AsyncAPI 1.0.12 (`-- … SEC-22 OWASP-API10`).
- Domain (step 6): above 10,000 and more than 3 decimal places → NoSubstituteProposed INV-12 (Red, Green `MAXIMUM_AMOUNT`, `MAXIMUM_DECIMAL_PLACES`); 10,000, 10000.000 and 0.001 accepted (green on activation, trial with exclusive bounds → refused).
- Gate trials: without the exception exit 1 (OpenAPI, then the AsyncAPI payloads); with it exit 0; LITER removed in either spec still exit 1.
- Reverse check of the fix: four drifts — ADR0014 "minor" → "non-major", QS-15 note, the CHANGELOG quote, the adapter and boundary tests above — all resolved.

---

# Security re-test — Cooking Assistance increment 2, step 14 fixes of SEC-20, SEC-21, SEC-22 — reviewer Claude Fable 5.1 (new reviewer)

## Package and target
Received: `package.md` (same rules as the first review), `report.md` (SEC-20 … SEC-23 with evidence and abuse cases), `fix.patch` and the repository copy `repo-copy-fix/` (e1c840a + the uncommitted fix: OpenAPI 1.0.16, AsyncAPI 1.0.12, `SubstituteIngredient.hasAmount` with `MAXIMUM_AMOUNT` 10,000 and 3 decimal places, INV-12 r19, ADR0014 addition, `tools/contract/oasdiff-err-ignore.txt`, the gate script with `--err-ignore`, three unit tests). Not read: ledger, journals, conversation history.

Target: larder-app in `repo-copy-fix/`, started by two probe ITs (`repo-copy-fix/larder-app/src/test/java/org/larder/security/SecurityRetestInc2IT.java`, run 1; `SecurityRetestInc2bIT.java`, run 2; `@SpringBootTest` RANDOM_PORT) on Testcontainers PostgreSQL 16-alpine and RabbitMQ 3.13, outbox relay and poller on, a Logback `ListAppender` on the root logger (run 1). AsyncAPI models generated into the copy with the original's pinned toolchain (`tools/codegen/node/node generate-asyncapi-models.mjs`, read-only use of the original; 32 models, `SubstituteIngredient(String name, double value, …)` as before). Build: the package's offline Maven command with `-Dit.test=SecurityRetestInc2IT` and then `-Dit.test=SecurityRetestInc2bIT`, one build at a time (`retest-run-1.log`, `retest-run-2.log`; both BUILD SUCCESS). Gate trials with the local Docker image `tufin/oasdiff:v1.33.0` (CI pins 1.34.1; the ignore semantics are the same), inputs and log in `gate-trials/` (`run.sh`, `gate-trials.log`).

Limits applied: 52 HTTP probes of 80 (47 + 5) and 10 AMQP publishes of 10 (9 + 1); no load or denial-of-service run; the largest body about 1.3 KB (a 1001-digit literal). Nothing outside local containers. Evidence ledgers: `retest-probe-results.txt` (run 1), `retest-probe-results-2.txt` (run 2).

Identities: Lena (cook, requester), Tom (cook, no community role), Jonas and Mia (cook + community), Marco (chef), Jonas with `help:read` only, no token; the Grandma Avatar context (`source: grandma-avatar`) and a sender with `source: community`.

## Verdicts per finding

### SEC-20 (high, API4 / A10 / A06) — FIXED
Evidence (run 1, request r1 = `01a1d012-2f68…`, Lena asked COMMUNITY + GRANDMA):
- Jonas, `value` 1e1500 → **400** VALIDATION_FAILED, detail `…/value: must have a maximum value of 10000` (R1-02); 1e999 → 400 (R1-03); 10000.001 → 400 (R1-04); 10000.0001 → 400 (R2-04); a 1001-digit literal → 400 "the body is not valid JSON" (R1-07b).
- Schema-valid but below the domain's precision: 0.0001 → **422** VALIDATION_FAILED, rule INV-12, title "Propose at least one substitute, each ingredient with its name and amount" (R1-06); 9999.9999 → 422 INV-12 (R1-07). 1e-1500 → 400 "exclusive minimum value of 0" (R1-05): the REST validator reads the literal as a double that underflows to 0, so it never reaches INV-12 — refused either way (same as B-22 of the first review).
- Nothing stored: `select count(*) from help` = 0 after the refusals (R1-08a); the request is still readable, status OPEN (R1-08, R1-08b), its helps page is `{"items":[]}` (R1-09).
- Accepted: 10000 → 201 (R1-10), 0.125 → 201 (R1-11), 1e4 → 201 (R1-12), 9999.999 → 201 (R2-02), 10000.000 → 201 (R2-03); 3 help rows (R1-12b). Afterwards Lena reads the 10000 help → 200 with `"value":10000` (R1-13), the request's helps → 200, 3 items (R1-14), Tom's `GET /helps?limit=100` → 200 (R1-15), Lena rejects the 10000 help → 200 (R1-16).
- No `IllegalStateException("Cannot read the stored answer")`, no ERROR line, no 500 in either run (R5-05: 0, 0, 0).

Residual (not a regression, no attacker path): the read side (`AnswerJsonMapper`, `helpView`) is unchanged, so a row that somehow held an unreadable answer (hand-edited, or written before the bound) would still fail the whole page. The bound closes both write paths (REST 400/422, broker dead letter), and no external client has existed, so no such row can be in the data. The second abuse case of SEC-20 ("page served with the bad row left out") is therefore not implemented — worth one line in the decision log, not a finding.

### SEC-21 (medium, A08) — FIXED
Evidence (run 2, request `01a1d014-74d1…`, all six channels bound as a consumer would):
- Jonas 10000, Mia 9999.999 and 0.125 over REST, Grandma 9999.999 over the broker → 4 helps, REST values `[10000, 9999.999, 0.125, 9999.999]` (S-06b).
- Outbox: rows 1–5 (HelpRequestCreated, 4 × HelpProvided) `published=true dead=false err=null`; dead-lettered rows 0 (S-07).
- Received on the probe queue bound to `cooking-assistance.help.provided`: 4 HelpProvided messages with `value` `10000.0`, `9999.999`, `0.125`, `9999.999`, each with zero violations of `HelpProvidedPayload` (S-08, S-09). Within the bound and 3 decimal places the `double` path is exact (`Double.toString` round-trips such values), so consumers and REST readers now see the same number.
- Run 1 (R5) showed `published=false` for every row with the WARN "no queue is bound for cooking-assistance.help-request.created": that was the probe's own setup (only `help.provided` bound) blocking the per-aggregate ordered relay at the first row, not the service — run 2 corrected the setup.

### SEC-22 (low, API10 / A10) — FIXED
Evidence (run 1, request r3 = `01a1d012-3ab5…`, Lena asked GRANDMA + COMMUNITY):
- Grandma, `value` 1e400 → dead letter on the first delivery, `x-death.count=1`, within about 1.5 s, exactly one WARN "Grandma Avatar answer <id> sent to the dead-letter queue: InvalidMessage", 0 "retrying" lines, no stack trace (R3-01). 10000.001 → the same (R3-02). A 1001-digit literal → dead letter at once, InvalidMessage ("not valid JSON" by Jackson's number-length constraint; R3-04). 0.0001 (schema-valid) → dead letter at once with `NoSubstituteProposed` (the domain's INV-12; R3-03). The request stays OPEN, help rows unchanged (R3-04b).
- A valid message (yogurt 200) → ACKed, request ANSWERED, one help (R3-05…R3-05e); a near-bound 9999.999 → ACKed, ANSWERED, 2 helps (R3-07…R3-07b).
- Across both runs: 0 "retrying" log lines (R5-05).

## Regression sample (changed operations only)
- provideHelp REST: Tom without the community role → 403 NOT_AUTHORIZED (R4-01); Marco (chef) as COMMUNITY → 403 (R4-02); Jonas as GRANDMA_AVATAR → 403 (R4-03); no token → 401 UNAUTHENTICATED (R4-04); `help:read` only → 403 INSUFFICIENT_SCOPE (R4-05); Jonas on his own request → 403 OWN_REQUEST INV-36 (R4-11); Jonas on a Grandma-only request → 403 PROVIDER_NOT_ASKED INV-01 (R4-13); Jonas repeats "Yogurt" after Lena's rejection → 422 SUBSTITUTE_REJECTED_BEFORE INV-15 (R1-17); one WARN per 401/403 with code and rule, no subject or text.
- Requester commands: Tom rejects / accepts Jonas's help on Lena's request → 403 NOT_REQUESTER (R4-06/07); Jonas accepts his own help → 403 (R4-08); Tom cancels Lena's request → 403 (R4-09).
- consumeGrandmaHelpProvided: `source: community` → dead letter, count 1 (R3-08); blank name → dead letter, count 1, `NoSubstituteProposed` (R3-09); the repeated rejected substitute → ACKed and ignored, one INFO "ignored: SubstituteRejectedBefore", status OPEN, 1 help (R3-06…R3-06b).
- Version header 1.0.15 (previous minor) still accepted on a read → 200 (R4-14). Help rows after everything: 7 in run 1, 4 in run 2 — only the accepted amounts.

## The gate exception (item 5)
`tools/contract/spec-compatibility.sh` passes `--err-ignore tools/contract/oasdiff-err-ignore.txt` to both oasdiff runs (OpenAPI and the AsyncAPI payloads rendered as OpenAPI); the AsyncAPI document diff and every other rule are unchanged. The file holds two lines, one per operation (`POST /help-requests/{helpRequestId}/helps`, `POST /consumeGrandmaHelpProvided`), each with the full oasdiff text for the one property and the one value (`… request property's max was set to \`10000.00\``). oasdiff ignores an error only when a line contains the operation's method and path and the error's complete text, so the lines are as narrow as the text.
Trials (`gate-trials/gate-trials.log`, oasdiff v1.33.0): T1/T3 base → fixed spec without the file: exactly one error each (`request-property-max-set`), exit 1. T2/T4 with the file: "No breaking changes", exit 0. T5/T7 the maximum set to 5000 instead, with the file: exit 1 (the value is part of the text). T6/T8 the enum value `LITER` removed, with the file: exit 1 (`request-property-enum-value-removed`). Verdict: narrow — it matches only these two errors. Hygiene observation: once the change is on main the lines match nothing, as ADR0014 says; they must be removed with the next spec change, or a later removal-and-re-adding of the same maximum would pass the gate silently.

## New findings
None. Candidates considered and dropped: the double serialisation of `value` in HelpProvided (`10000.0`) — spec-valid and exact within the bound; 1e-1500 answered by the validator's exclusive minimum rather than INV-12 — refused, fail-closed; the stale ignore lines — hygiene, recorded above.

## Observation (functional, not security, outside the changed operations)
O-7: after Lena rejected one of three helps on r1 the request went back to OPEN (US-CA-03 R5.E2 "sent again to both") and accepting one of the two remaining helps answered 409 "Not allowed while the help request is OPEN" (`HelpRequest.ensureAnswered`, R1-18/R1-19). The aggregate's accept guard is not part of the fix; the cook can only accept after a new answer arrives. For the owning step (3/6) to confirm or add an example; fail-closed, no finding.

## Not tested
The restricted broker user, security headers, management port, the poller with the new bound (unchanged code), Schemathesis with the new maximum (CI run of the fix PR will show it; the generator should now honour the bound), oasdiff 1.34.1 itself (the pinned version; v1.33.0 used locally).

## Original repository
`git -C /Users/annegretjunkercodecentricag/BlueprintImplementation status --short` before and after the re-test: the 13 modified files and `?? tools/contract/oasdiff-err-ignore.txt` of the moderator's fix plus the pre-existing `?? docs/cooking-assistance/ledger/wip/`; `git diff` there is byte-identical to `fix.patch` and the ignore file identical to the copy's. The original was read only (its `tools/codegen` toolchain run against the copy's spec, output into the copy).

## Counts
SEC-20 fixed · SEC-21 fixed · SEC-22 fixed · regressions 0 · new findings 0 · observations 1 (O-7) + 1 hygiene note · probes 52 HTTP / 10 AMQP · gate trials 8
