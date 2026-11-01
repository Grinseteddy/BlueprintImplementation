package org.larder.cookingassistance.adapter.in.rest;

import java.util.List;
import java.util.Objects;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Answer;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpLink;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** Maps the Helps DTOs of the OpenAPI contract to commands and results to responses. */
final class HelpMapper {

  private HelpMapper() {}

  static ProvideHelp.Command toCommand(Actor actor, HelpRequestId helpRequestId, HelpCreate body) {
    return new ProvideHelp.Command(
        actor,
        HelpProviderType.valueOf(body.getHelpProviderType().name()),
        helpRequestId,
        body.getAnswerTitle(),
        toAnswer(body.getAnswer()));
  }

  /** Only Preparation Step Explanations are built yet; the other answer kinds come later. */
  private static PreparationStepExplanation toAnswer(Answer answer) {
    return switch (answer) {
      case org.larder.cookingassistance.adapter.in.rest.generated.model.PreparationStepExplanation
              explanation ->
          new PreparationStepExplanation(
              new RecipeId(explanation.getRecipe()),
              new HowToStepId(explanation.getHowToStep()),
              explanation.getDescription(),
              // images are optional in the spec: no images, no pictures
              Objects.requireNonNullElse(explanation.getImages(), List.of()));
      default -> throw new NotImplemented("provideHelp with " + answer.getClass().getSimpleName());
    };
  }

  static HelpLink toLink(HelpRequestId helpRequestId, HelpId helpId) {
    return new HelpLink(
        Links.absolute(
            "/help-requests/{helpRequestId}/helps/{helpId}", helpRequestId.value(), helpId.value()));
  }
}
