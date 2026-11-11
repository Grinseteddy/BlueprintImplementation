-- Ingredient requests (B2-S01, US-CA-02, INV-11, INV-15): the ingredient's name as the client
-- sent it, taken as given (L3); notes carry over between requests by this name
alter table help_request add column ingredient_name text;
