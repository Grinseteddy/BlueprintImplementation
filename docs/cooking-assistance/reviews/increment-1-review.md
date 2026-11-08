# Independent review — Cooking Assistance increment 1 (Board B3)

Step 13, US-CA-03, slices B3-S02 extended to B3-S06. Moderator: the orchestrating session. Reviewers were separate agents in fresh contexts, read-only; the inspectors did not open the ledger or the journals.

## 1. Reverse check (documents against code)
- Agent: Sonnet, read-only, scoped to increment 1. Columns read from V1–V5 applied to a throw-away PostgreSQL 16.
- Matches: columns 16/16, names 44/45, states 12/13, enum values equal to the OpenAPI.
- Drifts 8.
  - 1 S3.E4 had no scenario and was missing from the inventory → defect, step 12: scenario `@B3-S05 @INV-39 @S3.E4` green on activation, its trial (accept without `ensureNotRejected`) failed only that scenario; inventory gains S3.E4.
  - 2 readiness counts, 3 cards S3.E2/E3 name "set aside, still open", 4 brief §1 (Help `isRejected`, note kinds), 5 brief B3-S06 "while Open" → document corrections.
  - 6 `FIELD_NOT_ALLOWED` and 8 `previousNotes` (same step of the same recipe) → OpenAPI 1.0.10 and CHANGELOG.
  - 7 unlisted codes → planned, with the container problems P1–P3.
- After the rework: full build and coverage gate green (297 + 128, 12 + 40; no gaps).

## 2. Fagan inspection
- Two inspectors, Sonnet, in parallel: domain (0 major, 6 minor, 3 questions), architecture (3 major, 9 minor, 2 questions).
- Architecture inspector read the ADRs 0008, 0010, 0012, 0013, 0015, 0016, 0019, 0020, 0021, quality-scenarios.md, the OpenAPI spec and changelog, about 45 main source files, migrations V1–V5, seven tests, the three workflows and schemathesis.toml. Not inspected: domain classes line by line, generated code, Gherkin steps and the acceptance driver, the AsyncAPI document content, test fixtures, most IT bodies beyond the quality scenarios.

| # | Sev | Defect | Decision |
|---|---|---|---|
| A-1 | major | Trace id does not travel with AMQP messages; help request id not on log lines (ADR0020, QS-14, AP0007) | reworked: `outbox.traceparent` (V6), sent as W3C `traceparent`, AsyncAPI 1.0.6; help request id in MDC |
| A-2 | major | After a long outage publication can take up to 60 s (backoff cap, ADR0015 L5); QS-04 requires ≤ 5 s (Q-A1) | reworked: on broker (re)connect pending rows with attempts > 0 are due at once; ADR0015 addition |
| A-3 | major | `ProvideGrandmaAvatarHelp` without `RetryOnConflict.once`: a conflict counts toward the dead-letter limit (ADR0010, QS-10) | reworked: step 8 r2, retried once |
| D-1 | minor | No state tests for accept on CLOSED, reject on CLOSED or CANCELLED, time-out on CLOSED | reworked: step 6 r2, four guard tests |
| D-2 | minor | `HelpRequestTimedOut.occurredAt` is the detection time; AsyncAPI says "when the last deadline passed" | deferred with Q-D1 |
| D-3 | minor | Blank cancellation note stored and published, blank rejection note refused | reworked: step 6 r2, blank = no note; INV-08 addition (step 3 r8) |
| D-4 | minor | Two comments contradict the code | reworked |
| D-5 | minor | Scenario S3.E4 not in HEAD | rejected: uncommitted rework of this step |
| D-6 | minor | R4.E3 has two scenarios | rejected: the split is documented (step 3 r2) |
| A-4 | minor | Grandma consumer dead-letters without log line, reason or message id | late answers settled by Q-A2; the log line for dead letters deferred |
| A-5 | minor | Time-out batch is one transaction: one failing request starves the later ones (QS-09) | reworked: savepoint per candidate (`UnitOfWork.inSavepoint`) |
| A-6 | minor | Package `adapter.in.scheduling` not recorded in ADR0008 | deferred |
| A-7 | minor | `/actuator/prometheus` is `permitAll` | rejected: ADR0020 decision; input to step 14 |
| A-8 | minor | `ProblemDetailsAdviceTest` lacks the five new rejections, cites OpenAPI 1.0.3 | reworked: step 9 r2, each of the 14 |
| A-9 | minor | contract.yml, schemathesis.toml and ITs name old spec versions | reworked: 1.0.10 |
| A-10 | minor | `positive_data_acceptance` off for 7 operations; contract and fault-injection jobs not in `verify` | rejected: exclusions decided in triage; long jobs outside `verify` by the method |
| A-11 | minor | List queries N+1, `previousNotes` unbounded, cursor accepts any well-formed UUID | rejected: N+1 accepted in ADR0016; cursor rule decided in step 11 |
| A-12 | minor | `TIME_OUT_BATCH_SIZE` between two bean methods | reworked |

Questions:
- Q-A1 (ADR0015's 60 s cap against QS-04's 5 s): answered by the A-2 design; verification by a real broker stop.
- Q-A2 (a Grandma answer after a cancel, an acceptance or a time-out): every late answer is ignored: acknowledged, no effect, no dead letter, INFO log with message id and state. The inbound refactor found a gap, an answer after its provider's deadline on a request still open (`AnswerTooLate`) was dead-lettered; defect, step 9 r3, test-first: acknowledged, INFO log with message id and reason.
- Q-D1 (`HelpRequestTimedOut.occurredAt`: deadline or detection), Q-D2 (`HelpRequestCreated` carrying `previousNotes`), Q-D3 (one of two Helps rejected: the request goes back to OPEN and can time out): deferred.

## 3. Rework
- Each defect test-first. Red for the predicted reason, Green, trials for tests green on activation.
- Prediction mismatches:
  - W1 (AmqpAdaptersIT): first run a setup fault, the exclusive probe queue deleted by the reset; named queue and a wait for the Grandma consumer (user).
  - Date bomb, on main too: `ApplicationWiringIT` and `AmqpAdaptersIT` A4 failed from 2026-11-07 (story-time fixtures against `Clock.systemUTC()`); `fixtures/StoryClock` (user).
  - E5b (BrokerOutageIT): with a 3 min outage the trial (listener off) passed too, because the attempts follow a 60 s grid; 3 min 30 s (user), the trial then failed 34.6 s after the return.
  - T3 scaffold (`OutboxRecord` + traceparent) broke 23 relay ITs, only the fast suite had been run; the claim query selects the column (user).
  - T4 trial ("null" header) failed 9 mapper tests instead of one: the mapper validates headers against `MessageHeader`, so the 1.0.6 pattern is enforced.
- Process slips 2: T2 and U2 run without a stated prediction.
- Finding (A-2): "attempts > 0" cannot be shown by a test, a never-failed row is always due; kept as the ADR's wording.
- Limit (A-3, user): no rollback support in the fakes; that Grandma's Help is kept after the retry is not shown by a fake-based test, rollback itself is proven on the real unit of work (`UnitOfWorkIT`).
- Limit (A-5): failed candidates stay Open and are claimed again (`order by h.id limit 100`). 1 to 99 permanently failing candidates end the drain of that poll; 100 or more at the lowest ids starve later due requests. No test has a failing candidate in the poller's loop. Planned, with QS-09.
- Planned list: the time-out ERROR line has the message only (no stack trace, "null" for an exception without a message); database down while the broker connection is re-established (the reconnect listener's update could fail the AMQP connection; next poll repairs the backlog), with the QS-05 follow-ups; an incoming Grandma message does not continue its trace; `POST /help-requests` has no MDC id; raw path text in the log (step 14).
- Later rework: step 9 r3, `AnswerTooLate` joins the late-answer catch (Q-A2 gap, found by the inbound refactor). Test-first.
- Refactor per layer (isolated agents one after another, `src/test` unchanged by `diff -r` against a snapshot, all kept by the user):
  - Domain: `Note.saysSomething` names the blank-note rule once.
  - Persistence: `CurrentTrace` package-private, `Outbox` Javadoc.
  - Application: `timeOutAlone`, `shouldClaimAgainAfter` Javadoc.
  - Inbound: rename to `shouldClaimAgainAfter`, `TimeOutPolling` comment, `HELP_REQUEST_ID` private; outcome pattern switch tried and undone.
  - Outbound: `OutboxRelay.deliver`, `OutboxRetrySchedule`.
- Document corrections from the refactors (user): INV-08 blank note (step 3 r8), ADR0021 savepoint addition, QS-14 trace id on every log line.
- Further mismatches, 2026-11-08:
  - Full `verify`: `AsyncApiContractIT` D2 failed, the date bomb again (fixture deadline against the real clock), on `main` too; `StoryClock` imported (user).
  - Fault injection: the three tests green, but the `quality.yml` command failed on `AcceptanceIT` (`NoTestsDiscovered`, `-Dgroups` filters the Cucumber suite to nothing), so the job could never have passed; `-Dit.test='!AcceptanceIT'` (user). Not yet run on GitHub.
- Process slip: an unsorted import failed Spotless once after the tests passed.
- Gates after the rework: `verify` green (317 + 135; 13 + 41; Spotless, Error Prone, Spectral), fault injection green (3, exact CI command offline), intent coverage exit 0, no gaps.

## 4. Reverse check of the rework and re-inspection
- Reverse check (Sonnet, read-only): 8 drifts, all pointing at documents. Drifts 1 to 7 corrected (ADR0015, ADR0020, ADR0021, INV-09, QS-14, README, AsyncAPI 1.0.7, OpenAPI 1.0.11, user); drift 8 needed no action.
- Re-inspection (Fable, read-only, rework files read in full): 11 items fixed, A-9 partially fixed, none not fixed. New: N-1 major (E5b's own `rabbit.receive` opens the connection, so the reconnect's production trigger is not measured; the listener container's default 5 s recovery misses QS-04's p95), N-2, N-3, N-5, N-6 minor; questions Q-1 to Q-3.
- Decisions (user): N-1 and N-6 reworked: the Grandma listener retries its connection every 1 s (`AmqpAdaptersIT` W2, Red 5000 against 1000), E5 and E5b read over their own connection, E5b deletes its queue in `finally`; trial with 60 s recovery: E5b failed at 29.5 s, E5 stayed green. N-5 reworked: `PictureLinkNotSecure` row, trial failed only that row. N-2 rejected (the default keeps the fakes unchanged), N-3 planned, A-9 rest rejected (versions valid in the enum), Q-1 planned (relay lines without the command's trace), Q-2 accepted as is, Q-3 confirmed (ADR0015: "the first time or again").
- Prediction mismatch: E5b took 0.31 s, 2 to 3 s predicted, the worst case given as the typical time; the log shows the listener's thread opening the service's connection. Accepted (user): a failed reconnect with stack trace every second during an outage (about 40 log lines a second); quieter logging planned.
- Process slip: the document script stopped on an anchor matching twice while the gates had been started beside it; the run was stopped and restarted on the finished state.
- Gates after this rework: `verify` green (318 + 136; 13 + 41; Spotless, Error Prone, Spectral), fault injection green (3, exact CI command offline; E5b 0.59 s), intent coverage exit 0, no gaps (INV-03 without a domain-level test remains noted).

## 5. Result
- Findings: 3 major and 15 minor from the inspection, 1 major and 4 minor from the re-inspection, 16 drifts over the two reverse checks. Every major reworked test-first; none left open.
- QS-04: met on test containers with a real broker stop (E5b, 3 min 30 s, measured through the service's own reconnect; 0.31 s and 0.59 s after `start_app` returned, 2 s worst case by design). Scaled against the scenario's 10 min, so indicative for production.
- Deferred, open questions for increment 2 (user): D-2 with Q-D1 (`HelpRequestTimedOut.occurredAt`: deadline or detection), Q-D2 (`HelpRequestCreated` carrying `previousNotes`), Q-D3 (one of two Helps rejected: back to OPEN, can time out). Planned list: A-4 (dead-letter log line), with the quieter reconnect logging. A-6 corrected: ADR0008 addition.
- Limits: the last rework (N-1, N-5, N-6) had no further independent review; its evidence is the Red, the two trials and the gates. `quality.yml` with the corrected command has not yet run on GitHub. INV-03 has no domain-level test.
