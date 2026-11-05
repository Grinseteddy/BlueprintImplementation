-- a rejection note names an existing Help (INV-06); checked at commit, because
-- Spring Data JDBC deletes and re-inserts the help and help_request_note rows on every save
alter table help_request_note
    add constraint fk_help_request_note_help foreign key (help_id) references help (id)
    deferrable initially deferred;
