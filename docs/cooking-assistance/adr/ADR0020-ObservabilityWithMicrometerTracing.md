ADR0020

# Observability with Micrometer Tracing and OpenTelemetry

### Adopted 2026-11-01

### Annegret Junker, Architect

## Decision
**We will trace with Micrometer Tracing over the OpenTelemetry bridge and expose Spring Boot Actuator health and readiness: the trace id appears on every log line and travels with HTTP requests and AMQP messages; the AsyncAPI `correlationId` header carries the help request id, which correlates one help journey.**

## Context
Question L8 from step 5, due in step 12 of increment 0. QS-14 asks for a correlation id on every log line, HTTP ↔ message, and an alert on outbox backlog.

## Options considered

### Micrometer Tracing + OpenTelemetry bridge

standard propagation (W3C traceparent) across HTTP and AMQP; exporters can be added without code changes

### Logs only

MDC correlation id set at each entry point; no tracing, nothing to export

## Consequences

- The walking skeleton tests one correlation through one request (QS-14, short check); outbox backlog metrics and alert routing come in increment 1
- Exporter and collector are an operations choice (open verification)

## Principles
AP0007

## Quality scenarios
QS-14

## Addition (QS-14 backlog metric, step 12 of increment 1, 2026-11-05, decided by the user)

- **Outbox backlog as Micrometer gauges:** the number of outbox rows neither published nor
  dead-lettered, and the age of the oldest such row in seconds. The alert thresholds of QS-14
  (backlog > 100 or oldest entry > 1 min) are tested against these values.
- **Exposed on `/actuator/prometheus`** next to the health probes (`management.endpoints.web.exposure`
  adds `prometheus`), with the same protection as the probes; nothing else of Actuator is exposed.
- Alert rules and their routing stay an operations choice (open verification).

## Addition (step 5 revision from step 13, increment 1, 2026-11-08, decided by the user)

- **What a log line carries:** the pattern is `[traceId-spanId] [helpRequestId]` (`logging.pattern.correlation`). The trace id appears where a span exists, on the log lines of an HTTP request. The relay, the time-out poller and the Grandma consumer run without a span.
- **Help request id in the MDC** under `helpRequestId`: set for requests that name a help request in their path, by the relay per row and by the Grandma consumer per message.
- **The trace travels with the message:** the command's W3C `traceparent` is stored in `outbox.traceparent` in the command's transaction and sent as the AMQP header `traceparent` (AsyncAPI 1.0.6); absent when the command ran without a trace.
- **Not yet (planned list):** relay log lines do not show the command's trace id, and an incoming Grandma message does not continue its trace.

## Addition (step 5 revision from step 14, increment 1, 2026-11-08, decided by the user)

- **Management on a port of its own** (security review SEC-16): health, readiness and metrics answer on `LARDER_MANAGEMENT_PORT` (default 8081), which the platform must keep off the public route; the API port answers 404 for them.
- **Security events are logged at WARN without personal data** (SEC-06, ADR0019): a refused caller (401, 403) with its code, the method and the still percent-encoded path, at most its first 200 characters (SEC-19), where the security filter answers; a dead-lettered Grandma Avatar message with its message id and the rejection's type. On these lines no text a sender controls reaches the log: a help request id or message id that is not a UUID is left out or replaced. The 403 lines of the application and the domain carry status and code (a domain refusal also its rule); the trace id and help request id come from the log pattern. Business refusals (422, 409) and 404 are not logged.
- **The OAuth resource metadata document** (`/.well-known/oauth-protected-resource`) is served by Spring Security, which has no switch to turn it off, and is not one of the API's operations, so it is not in the OpenAPI spec. It and the bearer challenge name the configured public base URL, never the request's Host, and declare no token binding (`false`) (SEC-15).
