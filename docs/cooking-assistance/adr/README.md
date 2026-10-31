# Cook Assistance — ADRs from blueprint step 5 (adopted 2026-10-31)

Format as in Larder's ADR0001–ADR0005. Each ADR names the principles it honors or overrides and the quality scenarios it serves. None overrides a principle.

| ID | Title | Serves |
|---|---|---|
| [ADR0006](ADR0006-Java21SpringBoot3.md) | Java 21, Spring Boot 3.5 and Maven | QS-01, QS-02, QS-16 |
| [ADR0007](ADR0007-CookAssistanceAsAMaven.md) | Cook Assistance as a Maven module of the Larder monolith | QS-02 |
| [ADR0008](ADR0008-HexagonalPackageLayoutWithArchitecture.md) | Hexagonal package layout with architecture tests | QS-01, QS-02 |
| [ADR0009](ADR0009-PostgresqlSchemaPerModuleSpring.md) | PostgreSQL schema per module, Spring Data JDBC, Flyway | QS-01, QS-05, QS-11 |
| [ADR0010](ADR0010-OptimisticLockingWithOneRetry.md) | Optimistic locking with one retry | QS-10 |
| [ADR0011](ADR0011-Uuidv7TypedIdsAndAn.md) | UUIDv7 typed IDs and an injected clock | QS-01, QS-09 |
| [ADR0012](ADR0012-JwtResourceServerAndPer.md) | JWT resource server and per-module broker user | QS-12, QS-13 |
| [ADR0013](ADR0013-AuthorizationScopesResourceChecksActor.md) | Authorization: scopes, resource checks, actor invariants | QS-12, QS-13 |
| [ADR0014](ADR0014-GeneratedContractCode.md) | Generated contract code | QS-15 |
| [ADR0015](ADR0015-TransactionalOutboxOrderedRelayIdempotent.md) | Transactional outbox, ordered relay, idempotent consumer | QS-04, QS-08, QS-10, QS-14 |
| [ADR0016](ADR0016-QueriesReadTheAggregateTables.md) | Queries read the aggregate tables | QS-07, QS-01 |
| [ADR0017](ADR0017-TestInfrastructureFastAndSlow.md) | Test infrastructure: fast and slow suites on real infrastructure | QS-01, QS-04, QS-05 |
| [ADR0018](ADR0018-OneBuildCommandHooksAnd.md) | One build command, hooks and CI | QS-16, QS-15 |
| [ADR0019](ADR0019-SecurityBaselineAndReviewTools.md) | Security baseline and review tools | QS-12, QS-13 |
| [SADR0008](SADR0008-HelpIsPartOfThe.md) | Help is part of the Help Request aggregate | QS-10, QS-01 |
