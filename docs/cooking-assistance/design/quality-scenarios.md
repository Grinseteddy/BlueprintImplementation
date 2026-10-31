# Quality scenarios — Cook Assistance (step 5b, 2026-10-31)

**Quality goals (ranked):**
1. **Maintainability** (testability, modifiability) — AP0003, AP0005
2. **Reliability** (fault tolerance, recoverability) — AP0004
3. **Security** (confidentiality, authenticity, accountability) — AP0008

Ties between decision options go to the higher-ranked goal.

**Priority** = importance × difficulty (H/M/L, ATAM utility tree).

**Verify** says how each scenario is checked:
- **test (12):** measured in step 12
- **review (13):** judged in step 13
- **open verification:** needs staging or production

| ID | Characteristic | Source | Stimulus | Environment | Artifact | Response | Response measure | Priority | Verify |
|---|---|---|---|---|---|---|---|---|---|
| QS-01 | Maintainability / testability | developer | changes a domain rule | development | Cook Assistance module | rule changed test-first; suite runs automatically | ≥ 95 % of acceptance examples automated (AP0005); domain line coverage ≥ 90 %; fast suite < 60 s | H/M | test (12) |
| QS-02 | Maintainability / modularity | developer | adds a feature | development | domain and application layers | domain code has no framework, persistence or messaging import | 0 violations of the architecture rule test | H/L | test (12) |
| QS-03 | Performance / time behavior | cooks and providers | 20 HTTP req/s and 10 msg/s at peak | normal operation | REST API, consumer | requests served | commands p95 ≤ 300 ms, queries p95 ≤ 200 ms | M/L | test (12), indicative on test infrastructure; production: open verification |
| QS-04 | Reliability / fault tolerance | broker | unavailable for 10 min | normal operation | outbox relay | commands still accepted; events kept in the outbox and published after recovery, in order per help request | 0 events lost; publication ≤ 5 s after the broker returns (p95) | H/M | test (12) — fault injection |
| QS-05 | Reliability / fault tolerance | database | unavailable | normal operation | REST API | commands and queries answered 503 Problem Details; no partial writes | 100 % 503, no half-written state; recovers without a restart | H/M | test (12) — fault injection |
| QS-06 | Reliability / availability, recoverability | operations | outage | production | module | restored | availability 99.5 %/month; RTO 1 h; RPO 5 min | H/H | open verification (operations: backup and HA of the shared Larder DB) |
| QS-07 | Reliability / consistency | requester | changes a help request, then reads it | normal operation | query side | sees their own change | read-your-writes: 100 % | H/M | test (12) |
| QS-08 | Interoperability / timeliness | Help Request aggregate | emits an event | normal operation | outbox relay → broker | event published | ≤ 5 s p95 from commit to broker | M/M | test (12) |
| QS-09 | Functional correctness / timeliness | clock | a provider's deadline passes without an answer | normal operation | timeout scheduler | Help request timed out (or that provider's side timed out) | fires ≤ 30 s after the deadline; exactly once per deadline, also with 2 instances | H/H | test (12) |
| QS-10 | Reliability / contention | Community and Grandma Avatar | answer the same request at the same time | normal operation, 2 instances | Help Request aggregate | both answers stored; no lost update | optimistic lock; one retry; 0 lost answers | H/M | test (12) |
| QS-11 | Capacity | cooks | ~50,000 help requests per year | normal operation | database | queries stay within QS-03 | QS-03 holds with 3 years of data (150k requests) | L/L | test (12), indicative |
| QS-12 | Security / confidentiality — data protection | Cook Profile | publishes "Cook deregistered" | normal operation | help requests, read models, outbox | personal data erased or anonymized (free text, notes, photo links) | ≤ 30 days; cook IDs only, never names or emails; no personal data in logs | H/M | both (test 12 for erasure, review 13 for logs) |
| QS-13 | Security / accountability | any actor | changes a help request's state | normal operation | audit trail | change recorded with actor, time, command | 100 % of state changes; kept 1 year | M/L | test (12) |
| QS-14 | Operability / monitoring (AP0007) | operations | outbox backlog grows | normal operation | metrics, alerting | alert raised | backlog > 100 or oldest entry > 1 min → alert; correlationId on every log line, HTTP ↔ message | M/M | test (12) for metrics and correlation; alert routing: open verification |
| QS-15 | Compatibility / co-existence | consumer team | receives a new API or event version | evolution | OpenAPI, AsyncAPI | change is additive | breaking diff fails CI (oasdiff, AsyncAPI diff); breaking change only as a new major with 6 months' deprecation | H/L | test (12) — CI gate |
| QS-16 | Maintainability / build | developer | pushes | CI | `verify` job | build, lint, all tests | < 10 min | M/L | test (12) — CI timing |

**Deferred or not measurable:** none.
