-- the notes the requester wrote when rejecting a Help or cancelling (INV-07, ADR0016 L6)
create table help_request_note (
    help_request_id uuid        not null references help_request (id),
    position        int         not null,
    kind            text        not null,
    text            text        not null,
    written_at      timestamptz not null,
    primary key (help_request_id, position)
);
