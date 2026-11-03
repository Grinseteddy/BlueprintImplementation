-- a rejected Help stays in the request, marked; never accepted later (B3-S05, INV-39)
alter table help add column rejected boolean not null default false;
