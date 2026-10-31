package org.larder.cookingassistance.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Persistence model of a Help (table help), a child row of help_request; answer as jsonb. */
@Table("help")
record HelpRow(
    @Id UUID id,
    String providerType,
    UUID providerPerson,
    String answerTitle,
    AnswerJson answer,
    boolean accepted) {}
