package org.larder.cookingassistance.adapter.in.rest;

import java.net.URI;
import java.util.stream.Collectors;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestLink;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

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

  /** Links are absolute, built from the request's base URL. */
  static HelpRequestLink toLink(HelpRequestId id) {
    URI link =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/help-requests/{helpRequestId}")
            .buildAndExpand(id.value())
            .toUri();
    return new HelpRequestLink(id.value(), link);
  }
}
