# Journal — increment 1, step 13 (Independent review)

Board B3, US-CA-03, slices B3-S02 extended to B3-S06. EXACT Coding at 88fb3f9, autonomy full-hitl.
Branch `feature/cooking-assistance-inc1-closing` from main 3adc361. Started 2026-11-06, finished
2026-11-08. The findings and their decisions are in `reviews/increment-1-review.md`; this journal
records how the work went.

## Reviews (separate read-only agents)
- Reverse check (Sonnet): 8 drifts, one defect (S3.E4 without a scenario), the rest documents.
- Fagan inspection (two inspectors, Sonnet, fresh contexts, in parallel): domain 0 major, 6 minor, 3
  questions; architecture 3 major, 9 minor, 2 questions.
- After the rework: reverse check (Sonnet) 8 drifts, all documents; re-inspection (Fable) 11 items
  fixed, A-9 partially, new N-1 major, N-2, N-3, N-5, N-6 minor, Q-1 to Q-3.

## Returns to earlier steps
- **Step 3:** r8 INV-08, a blank note counts as no note (D-3); r9 INV-09, a late Grandma answer is
  acknowledged and ignored (Q-A2).
- **Step 4:** r13 OpenAPI 1.0.10; r14 AsyncAPI 1.0.6 (`traceparent`, A-1); r15 AsyncAPI 1.0.7 (late
  answers); r16 OpenAPI 1.0.11 (blank note, INV-39 on `acceptHelp`).
- **Step 5 (user):** ADR0015 additions (the broker's return ends the backoff, A-2; listener recovery
  1 s, N-1; late answers, Q-A2); ADR0020 addition (what a log line carries, `outbox.traceparent`);
  ADR0021 additions (savepoint per candidate, A-5; claim again after a full batch); ADR0008 addition
  (`adapter.in.scheduling`, A-6); QS-14 corrected twice, the second time because "trace id on every
  log line" overclaimed for threads without a span.
- **Step 6 r2:** D-1 four guard tests, D-3 blank note. **Step 7:** V6 `outbox.traceparent`,
  `UnitOfWork.inSavepoint`. **Step 8 r2:** A-3 retried once. **Step 9 r2, r3:** A-8 error map; every
  late answer ignored, the `AnswerTooLate` gap found by the inbound refactor. **Step 10:** A-2
  reconnect ends the backoff; N-1 listener recovery. **Step 12:** S3.E4, E5b, QS-04 with a real stop.

## N-1 (re-inspection): the test measured its own client
- E5b read through the service's connection factory, so its `rabbit.receive` opened the connection
  that ends the backoff. In production that is the listener's recovery, 5 s by default.
- Test list T1 to T6 (approved 2026-11-08). T1 `AmqpAdaptersIT` W2 Red, 5000 for 1000, as predicted;
  it reads a private field, the getter being protected. T2 Green: `ContainerCustomizer` in
  `AmqpConsuming` (Spring Boot has no property for it).
- T3 **mismatch:** E5b 0.31 s, 2 to 3 s predicted; I gave the worst case as the typical time. The log
  shows the listener's thread opening the service's connection after the test's reader. Accepted.
- T4 trial (recovery 60 s): E5b failed at 29.5 s, E5 green, as predicted. Undone.
- T5/T6: `PictureLinkNotSecure` row green on activation; the trial failed only that row.
- Correction to the user: a failed reconnect logs a stack trace every second, about 40 lines a second
  during an outage, not one entry. Accepted by the user; quieter logging planned.

## Prediction mismatches
- W1 setup fault (exclusive probe queue deleted by the reset); date bomb in `ApplicationWiringIT`,
  `AmqpAdaptersIT` A4 and `AsyncApiContractIT` D2 (story-time fixtures against the real clock, on
  main too; `StoryClock`); E5b with a 3 min outage passed its trial too (attempts on a 60 s grid; 3
  min 30 s); T3 scaffold broke 23 relay ITs, only the fast suite run; T4 trial failed 9 mapper tests
  instead of one (the mapper validates headers); `quality.yml` failed on `AcceptanceIT`
  (`NoTestsDiscovered`), so the job could never have passed; E5b's time (above).

## Process slips
- T2 and U2 run without a stated prediction. An unsorted import failed Spotless once. A document
  script stopped on an anchor matching twice while the gates had been started beside it; the run was
  stopped and restarted on the finished state.

## Refactoring (isolated agents per layer, `src/test` unchanged by `diff -r`, all kept)
- Domain `Note.saysSomething`; persistence `CurrentTrace` package-private; application `timeOutAlone`;
  inbound `shouldClaimAgainAfter` (renamed by the user), outcome pattern switch tried and undone;
  outbound `OutboxRelay.deliver`, `OutboxRetrySchedule`.

## Final gates (2026-11-08)
- `./mvnw -o clean verify`: 318 + 136, 13 + 41; Spotless, Error Prone, Spectral. Fault injection,
  exact `quality.yml` command offline: 3 green, E5b 0.59 s. Intent coverage: exit 0, no gaps; INV-03
  without a domain-level test.
