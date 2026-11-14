-- Catastrophe requests (B4-S01, US-CA-04, INV-20, INV-38): 0..10 pictures in order; other kinds
-- of request carry none (decided 2026-11-13: text[])
alter table help_request add column pictures text[] not null default '{}';
