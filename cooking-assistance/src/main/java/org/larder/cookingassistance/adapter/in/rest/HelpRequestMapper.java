package org.larder.cookingassistance.adapter.in.rest;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.larder.cookingassistance.adapter.in.rest.generated.model.AskedProvider;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequest;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestStatus;
import org.larder.cookingassistance.adapter.in.rest.generated.model.ProviderState;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** Maps the Help Requests DTOs of the OpenAPI contract to commands and results to responses. */
final class HelpRequestMapper {

  private HelpRequestMapper() {}

  static RequestHelpWithPreparationStep.Command toCommand(Actor actor, HelpRequestCreate body) {
    return new RequestHelpWithPreparationStep.Command(
        actor,
        body.getTitle(),
        body.getDescription(),
        new RecipeId(body.getRecipe()),
        new HowToStepId(body.getHowToStep()),
        body.getPreferredProvider().stream()
            .map(provider -> HelpProviderType.valueOf(provider.name()))
            .collect(Collectors.toSet()));
  }

  static HelpRequestLink toLink(HelpRequestId id) {
    return new HelpRequestLink(
        id.value(), Links.absolute("/help-requests/{helpRequestId}", id.value()));
  }

  static HelpRequest toResponse(HelpRequestView view) {
    List<AskedProvider> asked =
        view.askedProviders().stream()
            .map(
                provider ->
                    new AskedProvider(
                        generatedType(provider.providerType()),
                        provider.deadline().atOffset(ZoneOffset.UTC),
                        ProviderState.fromValue(provider.state().name())))
            .toList();
    return new HelpRequest(
            view.id().value(),
            view.requester().value(),
            view.title(),
            org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType.fromValue(
                view.type().name()),
            view.description(),
            view.preferredProviders().stream()
                .map(HelpRequestMapper::generatedType)
                .collect(Collectors.toSet()),
            asked,
            HelpRequestStatus.fromValue(view.status().name()))
        .recipe(view.recipe().value())
        .howToStep(view.howToStep().value());
  }

  private static org.larder.cookingassistance.adapter.in.rest.generated.model.HelpProviderType
      generatedType(HelpProviderType type) {
    return org.larder.cookingassistance.adapter.in.rest.generated.model.HelpProviderType.fromValue(
        type.name());
  }
}
