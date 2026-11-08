# Larder — Cook Assistance

Larder is a modular monolith. This repository holds its first module, **Cook Assistance**
(`cooking-assistance`), and the thin host application that runs it (`larder-app`). A cook asks
for help with a preparation step, and the community or the Grandma Avatar answers.

Status: increment 0, the walking skeleton (user story US-CA-03, slices B3-S01 and B3-S02).
The design documents are in [`docs/cooking-assistance/`](docs/cooking-assistance/).

## Build and test

Prerequisites: JDK 21 and Docker (Testcontainers starts PostgreSQL 16 and RabbitMQ 3.13). The
build installs its own pinned Node.js for the generators and Spectral.

| Command | What it runs |
|---|---|
| `./mvnw test` | the fast suite, without containers |
| `./mvnw verify` | everything: code generation from both specs, Spotless and Error Prone, the fast and slow suites (Testcontainers), Spectral lint of both specs (ADR0017, ADR0018) |
| `python3 tools/coverage/intent_coverage.py docs/cooking-assistance/coverage/inventory-inc0.yaml .` | intent coverage after `verify`; exits 1 on a gap |
| `./mvnw -Psecurity -DskipTests verify` | SpotBugs with FindSecBugs (the security CI job, ADR0019) |

CI: [`verify.yml`](.github/workflows/verify.yml) on every push and pull request,
[`security.yml`](.github/workflows/security.yml) weekly and on pull requests (SpotBugs +
FindSecBugs, OWASP Dependency-Check; needs the repository secret `NVD_API_KEY`).

## Run the host

```sh
./mvnw -DskipTests package
java -jar larder-app/target/larder-app-0.1.0-SNAPSHOT.jar
```

Every endpoint and secret comes from the environment (ADR0019). The host refuses to start when
a required variable is missing or blank, and names it.

| Variable | Required | Meaning |
|---|---|---|
| `LARDER_DB_URL` | yes | JDBC URL of the PostgreSQL 16 database (ADR0009) |
| `LARDER_DB_USERNAME` | yes | the module's own database role |
| `LARDER_DB_PASSWORD` | yes | its password |
| `LARDER_DB_SOCKET_TIMEOUT` | no, default `10` | seconds after which a query to a database that stopped answering fails (503) instead of waiting without limit |
| `LARDER_BROKER_HOST` | yes | RabbitMQ host (ADR0012) |
| `LARDER_BROKER_USERNAME` | yes | the module's own broker user |
| `LARDER_BROKER_PASSWORD` | yes | its password |
| `LARDER_BROKER_PORT` | no, default `5671` | AMQP port |
| `LARDER_BROKER_SSL` | no, default `true` | TLS to the broker (amqps); `false` only for local brokers |
| `LARDER_JWKS_URI` | yes | the identity provider's JWKS endpoint (issuer `https://larder.org`, audience `cooking-assistance`, ADR0012) |
| `LARDER_PUBLIC_BASE_URL` | yes | the URL every link in a response starts with, e.g. `https://larder.org/cooking-assistance`; never taken from the request (SEC-10) |
| `LARDER_TIMEOUT_POLLER_ENABLED` | no, default `true` | whether this instance runs the time-out poller (ADR0021) |
| `LARDER_TIMEOUT_POLL_DELAY` | no, default `10s` | pause between two runs of the time-out poller, e.g. `250ms`, `10s` |

Only the health and readiness probes are exposed (`/actuator/health/**`, ADR0020).

## Before the first deployment

- **Database** (ADR0009): the administrator creates the module's role and the schema
  `cooking_assistance`, owned by that role. The role needs no rights on `public`. The module
  runs its Flyway migrations itself, as that role, inside its schema.
- **Broker** (ADR0012): the module's user gets configure and write on
  `^cooking-assistance(\..*)?$` and read on `^(cooking-assistance\..*|grandma-avatar)$`. The
  `grandma-avatar` exchange belongs to the Grandma Avatar context and must exist before the host
  starts; the module only binds its queue to it.
- **Existing queues** (ADR0015): dead letters go through the module's exchange
  `cooking-assistance.dlx`. A queue `cooking-assistance.grandma-help-provided` declared by an
  earlier build with the default dead-letter exchange refuses the new arguments
  (`PRECONDITION_FAILED`); delete it once, empty, before starting this version.

## Replay a dead-lettered outbox row

The relay dead-letters a row only when it can never be published: it breaks the AsyncAPI spec or
its event type is unknown (ADR0015, addition L5). Each dead letter is logged at ERROR with row id,
event type and reason. Broker errors are never dead-lettered; they are retried with a backoff of
1 s doubling up to 60 s; when the broker connection comes back, the rows waiting for it are due
at once and go out within seconds (ADR0015).

1. Fix the defect and deploy the fix first. A row replayed against the old build is dead-lettered
   again.
2. Find the dead letters:

   ```sql
   select id, aggregate_id, event_type, last_error, dead_lettered_at
     from cooking_assistance.outbox
    where dead_lettered_at is not null
    order by id;
   ```

3. Release the row; the next relay run publishes it:

   ```sql
   update cooking_assistance.outbox
      set dead_lettered_at = null, attempts = 0, next_attempt_at = '-infinity'
    where id = :id;
   ```

The message keeps its message id (derived from the row id), so consumers that already saw it drop
it. The replayed message arrives after the help request's later messages, which went out while it
was dead-lettered; until it is published, it holds back that help request's rows that are still
pending. A replay tool is on the planned list.
