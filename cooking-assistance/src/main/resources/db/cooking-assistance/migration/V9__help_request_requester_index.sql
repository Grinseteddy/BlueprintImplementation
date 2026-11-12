-- QS-11 (step 12 of increment 2, sent back to step 7): the earlier-notes query (INV-07, INV-15)
-- reads one cook's history, not every older request
create index help_request_requester on help_request (requester, id);
