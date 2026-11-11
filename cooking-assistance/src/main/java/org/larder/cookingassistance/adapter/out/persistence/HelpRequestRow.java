package org.larder.cookingassistance.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

/** Persistence model of a Help Request (table help_request); never used in the domain. */
@Table("help_request")
record HelpRequestRow(
    @Id UUID id,
    UUID requester,
    String title,
    String description,
    String type,
    UUID recipe,
    UUID howToStep,
    UUID ingredient,
    String ingredientName,
    String substituteRequirements,
    String status,
    @Version long version,
    @MappedCollection(idColumn = "help_request_id", keyColumn = "position")
        List<AskedProviderRow> askedProviders,
    @MappedCollection(idColumn = "help_request_id", keyColumn = "position") List<HelpRow> helps,
    @MappedCollection(idColumn = "help_request_id", keyColumn = "position") List<NoteRow> notes) {}
