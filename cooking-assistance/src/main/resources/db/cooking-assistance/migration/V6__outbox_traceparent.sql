-- ADR0020 (inspection A-1): the trace context of the command that wrote the row, sent with the
-- message as the W3C traceparent header; empty when the command ran without a trace
alter table outbox add column traceparent text;
