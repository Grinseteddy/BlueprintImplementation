package org.larder.cookingassistance.adapter.out.persistence;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.Note;
import org.larder.cookingassistance.domain.helprequest.NoteKind;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/**
 * Maps the Help Request aggregate to its persistence model and back (via reconstitute); the query
 * side reads note and asked-provider rows through the same conversions (ADR0016).
 */
final class HelpRequestMapper {

  private HelpRequestMapper() {}

  static HelpRequestRow toRow(HelpRequest request) {
    return new HelpRequestRow(
        request.id().value(),
        request.requester().value(),
        request.title(),
        request.description(),
        request.type().name(),
        request.recipe() == null ? null : request.recipe().value(),
        request.howToStep() == null ? null : request.howToStep().value(),
        request.ingredient() == null ? null : request.ingredient().value(),
        request.ingredientName(),
        request.substituteRequirements(),
        request.status().name(),
        request.version(),
        request.askedProviders().stream().map(HelpRequestMapper::toRow).toList(),
        request.helps().stream().map(HelpRequestMapper::toRow).toList(),
        request.notes().stream().map(HelpRequestMapper::toRow).toList());
  }

  static HelpRequest toDomain(HelpRequestRow row) {
    return HelpRequest.reconstitute(
        new HelpRequestId(row.id()),
        new CookId(row.requester()),
        row.title(),
        row.description(),
        HelpType.valueOf(row.type()),
        row.recipe() == null ? null : new RecipeId(row.recipe()),
        row.howToStep() == null ? null : new HowToStepId(row.howToStep()),
        row.ingredient() == null ? null : new IngredientId(row.ingredient()),
        row.ingredientName(),
        row.substituteRequirements(),
        row.askedProviders().stream().map(HelpRequestMapper::toDomain).toList(),
        HelpRequestStatus.valueOf(row.status()),
        row.helps().stream().map(HelpRequestMapper::toDomain).toList(),
        row.notes().stream().map(HelpRequestMapper::toDomain).toList(),
        row.version());
  }

  private static NoteRow toRow(Note note) {
    return new NoteRow(
        note.kind().name(),
        note.text(),
        note.writtenAt(),
        note.help().map(HelpId::value).orElse(null));
  }

  static Note toDomain(NoteRow row) {
    return new Note(
        NoteKind.valueOf(row.kind()),
        row.text(),
        row.writtenAt(),
        Optional.ofNullable(row.helpId()).map(HelpId::new));
  }

  private static AskedProviderRow toRow(AskedProvider asked) {
    return new AskedProviderRow(
        asked.providerType().name(), asked.deadline(), asked.state().name());
  }

  private static HelpRow toRow(Help help) {
    return new HelpRow(
        help.id().value(),
        help.providerType().name(),
        help.provider().map(CookId::value).orElse(null),
        help.answerTitle(),
        AnswerJsonMapper.toJson(help.answer()),
        help.isAccepted(),
        help.isRejected());
  }

  static Help toDomain(HelpRow row) {
    return Help.reconstitute(
        new HelpId(row.id()),
        new HelpProvider(
            HelpProviderType.valueOf(row.providerType()),
            Optional.ofNullable(row.providerPerson()).map(CookId::new)),
        row.answerTitle(),
        AnswerJsonMapper.toAnswer(row.answer()),
        row.accepted(),
        row.rejected());
  }

  static AskedProvider toDomain(AskedProviderRow row) {
    return new AskedProvider(
        HelpProviderType.valueOf(row.providerType()),
        row.deadline(),
        ProviderState.valueOf(row.state()));
  }
}
