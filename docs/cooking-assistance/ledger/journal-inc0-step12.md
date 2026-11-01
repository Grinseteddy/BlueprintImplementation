# Journal — increment 0, step 12 (Tests)

## Slice check (decisions 2026-11-01)
- L8 → ADR0020 Micrometer Tracing + OTel bridge, Actuator; correlationId header stays helpRequestId.
- Examples the service cannot fully show: R3.E1 reworded (step 3 r1); R4.E1 partial (shown via HelpProvided, "Lena sees" in inc 1); R2.E3 via PATCH → 405.
- Step 4 r5: OpenAPI 1.0.4 with request-body and path-parameter examples (Spectral clean).
- Delivery: all of ADR0018 (Spotless, Error Prone, pre-commit, CI verify, ArchUnit).

## Test lists
- A AmqpAdaptersIT 7, B CompositionRootIT 1, C feature US-CA-03 (13 active-to-be @wip, 12 @planned), D OpenApiExamplesIT 1 + AsyncApiContractIT 1, E ArchitectureRulesTest 1 + CorrelationIT 1, F coverage script.
- Setup mismatch: Cucumber 7.31.0 needs JUnit Platform 1.13 (NoClassDefFoundError DiscoveryIssueReporter); Boot 3.5.6 manages 5.12.2 → Cucumber 7.23.0 (built on 5.12.2). Then verify green as predicted (larder-app 29 skipped = 4 ITs + 25 filtered scenarios).

## Cycles (AmqpAdaptersIT, RabbitMQ 3.13 Testcontainers)
- A1: Red NOT_FOUND no exchange 'cooking-assistance' as predicted; Green AmqpPublishing (exchange) + AmqpMessagePublisher (properties, headers, persistent); one compile slip (MessageBuilder support type).
- A2: Red no throwable as predicted; Green correlated confirms (module property file) + wait with timeout.
- A3: Red no throwable as predicted; Green mandatory + publisher returns, returned → throw.
- A4: Red no help stored (no queue bound) as predicted; Green AmqpConsuming (quorum queue, DLX → cooking-assistance.dlq, DLQ, binding from grandma-avatar — exchange declared idempotently) + GrandmaHelpProvidedListener (manual ack, ACK). Full verify green; ITs without a broker log "Connection refused" (noise, noted).
- A5: test compile slip (x-death generic map); Red DLQ 0 as predicted; Green basicReject without requeue (x-death reason rejected).
- A6: Red DLQ 0 as predicted; Green basicNack requeue + x-delivery-limit 4 — predicted that RabbitMQ counts allowed redeliveries (limit 4 = 5 deliveries): exactly 5 consumer calls, matched.
- A7: Red null as predicted; Green OutboxPolling (@Scheduled fixedDelay 1 s, @EnableScheduling, property guard; off in src/test/resources, on in AmqpAdaptersIT).
- Unit A 7/7; clean verify ×2 green. Refactor: exhaustive switch over Outcome in the listener.
- Unit C, R1.E1 (drives the composition root B): setup errors first — building larder-app alone missed transitive AMQP (build through the reactor, accept.sh); the test jar carried the module's test applications → duplicate JDBC repository registration (test jar restricted to fixtures); the glue's @Component driver was created before the port existed (driver built in @Before). Red then as predicted: "Could not resolve placeholder 'cooking-assistance.security.jwk-set-uri'". Green step 1 (JWKS URI from LARDER_JWKS_URI): cause as predicted (no migrations), surfaced in the test's own @Before instead of the POST (minor mismatch). Green step 2 (Flyway + schema in larder-app application.yml): R1.E1 green end to end (HTTP → PostgreSQL → outbox → poller → RabbitMQ → probe queue, payload valid against AsyncAPI).
- R1.E2, R1.E3, R1.E4: steps written; each green on activation (run per activation); trials: first-provider-only → only R1.E2; free plan refused → R1.E1–E3 (403); no edge validation → only R1.E4 (422) — all as predicted. Negative check 'nobody receives' uses the suite's only fixed wait (2 s, documented).
- R2.E1, R2.E2, R2.E3, R3.E1: green on activation (run per activation); trials (predictions refined before running): step from recipe → R1.E1, R2.E1–E3; temporary PATCH endpoint → R2.E3 with 500 (the edge interceptor rejects a handler that is not in the spec); no edge validation → R1.E4 (422), R3.E1 (201) — all as predicted. 8 of 13 scenarios active.
- R4.E1, R4.E3, S3.E1–E3: steps written. **Process slip:** the activation loop went on after R4.E3 failed (S3.E1–E3 were green). R4.E3 error: probe queues were auto-delete — a timed receive subscribes and leaves, so the second read found the queue deleted (test setup; fixed: non-auto-delete probe queues). Then 13/13 active scenarios green.
- Trials: A images dropped → only R4.E1; B provideHelp no-op → R4.E3 and S3.E1; C no own-request guard → only S3.E1; E invalid messages acked → only S3.E3 — all as predicted. **Trial D (rejections acked) — two invalid runs:** first the trial code did not compile ("already been caught") and the helper read the previous report (trial C's) as if it were D's; then a rewritten trial passed all scenarios because the incremental build missed the quick source swap. Third run (with a temporary dead-letter diagnostic in the driver, reverted): only S3.E2 fails, as predicted; the diagnostic showed S3.E3's dead letter with x-death reason rejected. **Lesson:** trial runs use a clean build and check exit code and report freshness before reading results (accept.sh now runs clean verify). Restored state confirmed with a clean build: 13/13 green.
