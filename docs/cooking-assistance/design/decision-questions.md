# Step 5 — Architecture decisions: pre-fill and open questions (2026-10-31)

## 5c. Already settled

| Checklist item | Settled by | Decision |
|---|---|---|
| Deployment shape | ADR0001 (Adopted) | Larder is a monolith; Cook Assistance is a module (AP0001) |
| Database instance | ADR0002 (Adopted) | one database instance; option "schemas per Bounded Context, own DB user per module" |
| Broker | ADR0003 (Adopted) | RabbitMQ, asynchronous between contexts (AP0002) |
| Sync API style | ADR0004 (Adopted) | REST for UIs (AP0006) |
| UI composition | ADR0005 (Adopted) | AppShell orchestrator; out of scope (backend only) |
| Chef exclusivity, naming | SADR0001, SADR0003, SADR0004 | in the brief and the specs |
| Error format, domain error → HTTP | step 4 (approved specs, P11) | RFC 9457 Problem Details; 403 actor rules, 409 INVALID_STATE, 422 rule violations, 404 unknown |
| API security scheme | step 4 (approved specs) | OAuth 2.0 bearer (scopes help:read, help:write); AMQP user-password |

## 5d. Open questions

Each question lists the quality scenarios it serves (QS) and the principles involved (AP). The tag says when it is asked:
- **now:** the walking skeleton needs it
- **later:** asked at the step and increment named

| # | Question | Why it matters | Options | Recommendation (serves) | When |
|---|---|---|---|---|---|
| 1 | Language, framework, build tool | everything below; EXACT ships stack profiles for TypeScript + Vitest and Java + JUnit 5 + Maven | **a)** Java 21 + Spring Boot 3.5 + Maven · **b)** TypeScript + NestJS + Vitest · **c)** Kotlin + Spring Boot + Gradle (EXACT's Java profile, adapted) | **a)** — matches an EXACT profile; Spring Modulith can verify module boundaries in a monolith (QS-01, QS-02, AP0001, AP0003) | now |
| 2 | Monolith hosting: how the module lives in Larder | ADR0001 says module; there is no Larder codebase yet | **a)** Maven multi-module: `cooking-assistance` module plus a thin `larder-app` Spring Boot host · **b)** standalone app now, merged later | **a)** — honors ADR0001 now; other contexts join as modules (AP0001, QS-02) | now |
| 3 | Package layout | hexagonal is required by the blueprint | **a)** hexagonal: `domain`, `application`, `adapter.in.rest`, `adapter.in.messaging`, `adapter.out.persistence`, `adapter.out.messaging`; ArchUnit rules · **b)** layered | **a)** (QS-02, AP0003) | now |
| 4 | Persistence style, database, data access, migrations | D1: Help Request with Helps as one aggregate | **a)** state-stored tables (`help_request` + child tables `help`, `asked_provider`, `note`), PostgreSQL 16, schema `cooking_assistance` with its own DB user, Spring Data JDBC (aggregate-oriented), Flyway · **b)** same with JPA/Hibernate · **c)** event store | **a)** — aggregate maps 1:1, no lazy-loading surprises (QS-01, QS-05, ADR0002) | now |
| 5 | Concurrency | QS-10: Community and Grandma answer at the same time | **a)** optimistic locking (`version` column); application service retries once on conflict · **b)** pessimistic lock | **a)** (QS-10) | now |
| 6 | ID strategy and time source | typed IDs; deadlines must be testable | **a)** UUIDv7 generated in the application, typed IDs in the domain; `java.time.Clock` injected; times stored as UTC `timestamptz` · **b)** DB-generated IDs | **a)** (QS-01, QS-09) | now |
| 7 | Authentication | who calls: cooks, chefs and community members via the AppShell (bearer token); Grandma Avatar via the broker | **a)** service validates JWTs as a resource server (issuer `https://larder.org`, audience `cooking-assistance`, JWKS, 60 s clock skew); broker: own RabbitMQ user per module, vhost permissions (write `cooking-assistance` exchange, read own queues only); identity reaches the application as an `Actor` value (id, roles, premium) · **b)** gateway validates, module trusts a header | **a)** — defense in depth, no trusted header (AP0008, QS-12) | now |
| 8 | Authorization | which board actor may issue which command; deny by default | **a)** scopes at the inbound adapter (`help:read`, `help:write`); resource checks in the application service (requester only for cancel/accept/reject; caller's provider type must be asked; chef help visible to the requester only → 403); actor rules in the domain as invariants (INV-36 own request); roles from token claims: `cook`, `chef`, `community`; premium from claim (until question L1) · **b)** external policy engine (OPA) | **a)** (AP0008, QS-12) | now |
| 9 | Code generators | inbound and messaging layer generated from the approved specs | **a)** OpenAPI: `openapi-generator-maven-plugin` 7.x, generator `spring`, `interfaceOnly`, `useSpringBoot3`, output `target/generated-sources`. AsyncAPI: models with AsyncAPI Modelina via the AsyncAPI CLI (pinned), Java records, output `target/generated-sources` · **b)** hand-written DTOs | **a)** — spec drift becomes a compile error (QS-15) | now |
| 10 | Broker client, outbox, delivery, consumer idempotency | the skeleton publishes (`HelpRequestCreated`, `HelpProvided`) and consumes (`GrandmaHelpProvided`); QS-04: no event lost | **a)** Spring AMQP; transactional outbox table written in the aggregate's transaction; one relay per instance with `FOR UPDATE SKIP LOCKED`, ordered per help request, exponential backoff (1 s → 60 s), publisher confirms; consumer: `processed_message` table in the same transaction (dedup by `messageId`), manual ack, max 5 deliveries then dead-letter queue `cooking-assistance.dlq` · **b)** Spring Modulith event publication registry · **c)** publish after commit (can lose events) | **a)** — meets QS-04 and QS-08 explicitly, testable (QS-04, QS-08, QS-10) | now |
| 11 | Query side for the skeleton | `getHelpRequestById` after the command; QS-07 read-your-writes | **a)** queries read the aggregate tables directly, in the command's database (no separate projection yet); projections added when a read model needs another shape (increment 1: `previousNotes`, provider lists) · **b)** separate projection tables from day one | **a)** (QS-07, QS-01) | now |
| 12 | Test infrastructure and property testing | fast/slow split; skeleton has cardinality rules (INV-01 providers 1..2, INV-38 ≤ 10 pictures) | **a)** JUnit 5 + AssertJ; fast suite `./mvnw test` (no containers); slow suite `./mvnw verify` with Testcontainers PostgreSQL 16 and RabbitMQ 3.13 (reused per run); jqwik for properties (1,000 tries, fixed seed in CI); Cucumber-JVM for acceptance; Schemathesis for the OpenAPI contract · **b)** H2 + embedded broker | **a)** — real infrastructure (QS-01, QS-04, QS-05) | now |
| 13 | Build, hooks, CI | the one command, formatter and linter for hooks, GitHub Actions `verify` | **a)** Maven wrapper; `./mvnw verify` generates, builds and runs all tests; Spotless (google-java-format); Error Prone; Spectral lint of both specs; `pre-commit` hooks: Spotless check, gitleaks, commitlint; GitHub Actions `verify` on push and PR (< 10 min, QS-16) · **b)** Gradle | **a)** (QS-16, AP0005) | now |
| 14 | Security baseline and step 14 tools | step 14 runs in increment 0 | **a)** secrets only from environment variables (Spring config, no committed secrets); request body limit 1 MB; security headers via Spring Security defaults; security events logged without personal data; tools: OWASP Dependency-Check, gitleaks, SpotBugs + FindSecBugs, OWASP ZAP baseline against the running service · **b)** decide at step 14 | **a)** (AP0008, QS-12) | now |
| 15 | Record D1 (Help as entity of Help Request) | deviates from the glossary model and SADR0004 ("aggregate root") | **a)** new SADR0008 "Help is part of the Help Request aggregate", amending SADR0004 · **b)** no record | **a)** — no silent deviation (AP0003) | now |
| L1 | Premium status source | INV-28 NOT_PREMIUM; closing on premium end (Cook Profile / Paywall) | token claim · local replica from events (AP0002) · sync call | — | later: step 8, increment 4 |
| L2 | Chef availability and time zone | INV-30 CHEF_UNAVAILABLE | replica of Chef calendars · sync query | — | later: step 8, increment 4 |
| L3 | Recipe, step and ingredient validation (D6) | references taken as given | replica of Recipe Catalog events · sync read · keep as given | — | later: step 8, increment 1 |
| L4 | Timeout scheduler | B3-S06; QS-09 exactly once with 2 instances | DB-polled deadlines with `SKIP LOCKED` · ShedLock · Quartz cluster | — | later: step 8, increment 1 |
| L5 | Retry details beyond the skeleton defaults, DLQ handling and replay | QS-04 | — | — | later: step 10, increment 1 |
| L6 | Paging implementation (cursor encoding), projections | list GETs | — | — | later: step 11, increment 1 |
| L7 | Erasure on "Cook deregistered" | QS-12 | consume the Cook Profile event, anonymize | — | later: step 8, increment 1 |
| L8 | Observability stack | QS-14 | Micrometer + OpenTelemetry Java agent · logs only | — | later: step 12, increment 0 |
| L9 | Load and fault-injection tools | QS-03, QS-04, QS-05 | Gatling / k6; Toxiproxy | — | later: step 12, increment 1 |
| G1 | ADR0005 overrides the Micro-UIs principle without acknowledgment; duplicate ID AP0005; AP0007 copy-paste | governance (step 1) | — | for the architecture owner, not this run (backend only) | logged |
