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
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** Maps the Help Request aggregate to its persistence model and back (via reconstitute). */
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
        request.status().name(),
        request.version(),
        request.askedProviders().stream().map(HelpRequestMapper::toRow).toList(),
        request.helps().stream().map(HelpRequestMapper::toRow).toList());
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
        row.askedProviders().stream().map(HelpRequestMapper::toDomain).toList(),
        HelpRequestStatus.valueOf(row.status()),
        row.helps().stream().map(HelpRequestMapper::toDomain).toList(),
        row.version());
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

  private static Help toDomain(HelpRow row) {
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

  private static AskedProvider toDomain(AskedProviderRow row) {
    return new AskedProvider(
        HelpProviderType.valueOf(row.providerType()),
        row.deadline(),
        ProviderState.valueOf(row.state()));
  }
}
