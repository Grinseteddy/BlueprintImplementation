-- a rejection note names the Help it rejected (INV-06, OpenAPI Help.rejectionNote)
alter table help_request_note add column help_id uuid;
