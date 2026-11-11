package org.larder.cookingassistance.adapter.out.persistence;

import java.net.URI;
import org.larder.cookingassistance.adapter.messaging.generated.Answer;
import org.larder.cookingassistance.adapter.messaging.generated.PreparationStepExplanation;
import org.larder.cookingassistance.adapter.messaging.generated.PreparationStepExplanationAnswerType;
import org.larder.cookingassistance.adapter.messaging.generated.Substitute;
import org.larder.cookingassistance.adapter.messaging.generated.SubstituteIngredient;
import org.larder.cookingassistance.adapter.messaging.generated.Substitutes;
import org.larder.cookingassistance.adapter.messaging.generated.SubstitutesAnswerType;
import org.larder.cookingassistance.adapter.messaging.generated.Unit;
import org.larder.cookingassistance.domain.helprequest.HelpAnswer;

/** Turns a Help's answer into the message model generated from the AsyncAPI spec. */
final class AnswerMessageMapper {

  private AnswerMessageMapper() {}

  /** The answer in its AsyncAPI shape, one message type per kind of answer. */
  static Answer toMessage(HelpAnswer answer) {
    return switch (answer) {
      case org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation explanation ->
          new PreparationStepExplanation(
              PreparationStepExplanationAnswerType.PREPARATION_STEP_EXPLANATION,
              explanation.recipe().value(),
              explanation.howToStep().value(),
              explanation.description(),
              explanation.images().stream().map(URI::toString).toList(),
              null);
      case org.larder.cookingassistance.domain.helprequest.Substitutes substitutes ->
          new Substitutes(
              SubstitutesAnswerType.INGREDIENT_SUBSTITUTE,
              substitutes.recipe().value(),
              substitutes.substitute().stream().map(AnswerMessageMapper::substitute).toList(),
              null);
    };
  }

  private static Substitute substitute(
      org.larder.cookingassistance.domain.helprequest.Substitute substitute) {
    return new Substitute(
        substitute.ingredient().value(),
        substitute.substituteIngredients().stream()
            .map(AnswerMessageMapper::substituteIngredient)
            .toList(),
        null);
  }

  private static SubstituteIngredient substituteIngredient(
      org.larder.cookingassistance.domain.helprequest.SubstituteIngredient ingredient) {
    return new SubstituteIngredient(
        ingredient.name(),
        ingredient.value().doubleValue(),
        Unit.fromValue(ingredient.unit().name()),
        null);
  }
}
