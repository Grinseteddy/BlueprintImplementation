package org.larder.cookingassistance.adapter.in.rest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Answer;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Help;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpPage;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Rejection;
import org.larder.cookingassistance.application.AcceptHelp;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.HelpFilter;
import org.larder.cookingassistance.application.HelpView;
import org.larder.cookingassistance.application.Page;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.application.RejectHelp;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpAnswer;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteIngredient;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.domain.helprequest.Unit;
import org.springframework.lang.Nullable;

/** Maps the Helps DTOs of the OpenAPI contract to commands and results to responses. */
final class HelpMapper {

  private HelpMapper() {}

  static ProvideHelp.Command toCommand(Actor actor, HelpRequestId helpRequestId, HelpCreate body) {
    return new ProvideHelp.Command(
        actor,
        HelpVocabulary.toDomain(body.getHelpProviderType()),
        helpRequestId,
        body.getAnswerTitle(),
        toAnswer(body.getAnswer()));
  }

  /** Preparation Step Explanations and Substitutes are built; the other kinds come later. */
  private static HelpAnswer toAnswer(Answer answer) {
    return switch (answer) {
      case org.larder.cookingassistance.adapter.in.rest.generated.model.PreparationStepExplanation
              explanation ->
          new PreparationStepExplanation(
              new RecipeId(explanation.getRecipe()),
              new HowToStepId(explanation.getHowToStep()),
              explanation.getDescription(),
              // images are optional in the spec: no images, no pictures
              Objects.requireNonNullElse(explanation.getImages(), List.of()));
      case org.larder.cookingassistance.adapter.in.rest.generated.model.Substitutes substitutes ->
          new Substitutes(
              new RecipeId(substitutes.getRecipe()),
              substitutes.getSubstitute().stream().map(HelpMapper::toSubstitute).toList());
      default -> throw new NotImplemented("provideHelp with " + answer.getClass().getSimpleName());
    };
  }

  private static Substitute toSubstitute(
      org.larder.cookingassistance.adapter.in.rest.generated.model.Substitute substitute) {
    return new Substitute(
        new IngredientId(substitute.getIngredient()),
        substitute.getSubstituteIngredients().stream()
            .map(HelpMapper::toSubstituteIngredient)
            .toList());
  }

  private static SubstituteIngredient toSubstituteIngredient(
      org.larder.cookingassistance.adapter.in.rest.generated.model.SubstituteIngredient
          ingredient) {
    return new SubstituteIngredient(
        ingredient.getName(), ingredient.getValue(), Unit.valueOf(ingredient.getUnit().name()));
  }

  static AcceptHelp.Command toAcceptance(Actor actor, HelpRequestId helpRequestId, HelpId helpId) {
    return new AcceptHelp.Command(actor, helpRequestId, helpId);
  }

  static RejectHelp.Command toRejection(
      Actor actor, HelpRequestId helpRequestId, HelpId helpId, Rejection body) {
    // F3: nextChef belongs to menu requests (increment 4)
    if (body.getNextChef() != null) {
      throw new NotImplemented("rejectHelp nextChef");
    }
    return new RejectHelp.Command(actor, helpRequestId, helpId, body.getNote());
  }

  static HelpLink toLink(String publicBaseUrl, HelpRequestId helpRequestId, HelpId helpId) {
    return new HelpLink(
        Links.absolute(
            publicBaseUrl,
            "/help-requests/{helpRequestId}/helps/{helpId}",
            helpRequestId.value(),
            helpId.value()));
  }

  /** An absent query parameter does not narrow the list. */
  static HelpFilter toFilter(
      @Nullable UUID helpProvider,
      @Nullable org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType type) {
    return new HelpFilter(
        Optional.ofNullable(helpProvider).map(CookId::new),
        Optional.ofNullable(type).map(HelpVocabulary::toDomain));
  }

  static HelpPage toPage(Page<HelpView> page) {
    return new HelpPage(page.items().stream().map(HelpMapper::toResponse).toList())
        .nextCursor(Cursors.next(page, view -> view.id().value()));
  }

  static Help toResponse(HelpView view) {
    return new Help(
            view.id().value(),
            view.helpRequest().value(),
            view.helpRequester().value(),
            view.answerTitle(),
            HelpVocabulary.toContract(view.provider().type()),
            toResponse(view.answer()),
            view.accepted())
        .helpProvider(view.provider().person().map(CookId::value).orElse(null))
        .rejectionNote(view.rejectionNote().orElse(null));
  }

  private static Answer toResponse(PreparationStepExplanation answer) {
    return new org.larder.cookingassistance.adapter.in.rest.generated.model
            .PreparationStepExplanation(
            org.larder.cookingassistance.adapter.in.rest.generated.model.PreparationStepExplanation
                .AnswerTypeEnum.PREPARATION_STEP_EXPLANATION,
            answer.recipe().value(),
            answer.howToStep().value(),
            answer.description())
        .images(OptionalArrays.absentWhenEmpty(answer.images()));
  }
}
