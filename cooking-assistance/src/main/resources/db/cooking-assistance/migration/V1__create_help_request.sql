-- Cook Assistance, schema cooking_assistance (ADR0002, ADR0009)

-- Help Request aggregate root (B3-S01, US-CA-03)
create table help_request (
    id          uuid primary key,
    requester   uuid        not null,
    title       text        not null,
    description text        not null,
    type        text        not null,
    recipe      uuid,
    how_to_step uuid,
    status      text        not null,
    version     bigint      not null
);

-- asked providers of a help request: child rows of the aggregate (INV-01, INV-09)
create table asked_provider (
    help_request_id uuid        not null references help_request (id),
    position        int         not null,
    provider_type   text        not null,
    deadline        timestamptz not null,
    state           text        not null,
    primary key (help_request_id, position)
);

-- Helps: entities inside the Help Request aggregate (B3-S02, SADR0008)
create table help (
    id              uuid primary key,
    help_request_id uuid    not null references help_request (id),
    position        int     not null,
    provider_type   text    not null,
    provider_person uuid,
    answer_title    text    not null,
    answer          jsonb   not null,
    accepted        boolean not null,
    unique (help_request_id, position)
);

-- transactional outbox (ADR0015)
-- event columns written by the repository; delivery columns owned by the relay (step 10)
create table outbox (
    id               bigserial primary key,
    aggregate_type   text        not null,
    aggregate_id     uuid        not null,
    event_type       text        not null,
    payload          jsonb       not null,
    occurred_at      timestamptz not null,
    published_at     timestamptz,
    attempts         int         not null default 0,
    next_attempt_at  timestamptz not null default '-infinity',
    last_error       text,
    dead_lettered_at timestamptz
);

-- the relay claims unpublished rows in order per help request (ADR0015)
create index outbox_claim on outbox (aggregate_id, id)
    where published_at is null and dead_lettered_at is null;

-- processed messages of idempotent consumers (ADR0015)
create table processed_message (
    message_id uuid primary key
);
