-- Ingredient requests (B2-S01, US-CA-02, INV-11): the one ingredient and what a substitute must
-- meet; references to the Recipe Catalog are plain ids, taken as given (D6)
alter table help_request add column ingredient uuid;
alter table help_request add column substitute_requirements text;
