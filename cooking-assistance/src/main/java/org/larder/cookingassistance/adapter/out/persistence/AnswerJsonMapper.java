package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.HelpAnswer;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteIngredient;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.domain.helprequest.Unit;

/** Writes and reads a Help's answer as JSON under the AsyncAPI field names. */
final class AnswerJsonMapper {

  private static final String INGREDIENT_SUBSTITUTE = "INGREDIENT_SUBSTITUTE";

  private AnswerJsonMapper() {}

  static AnswerJson toJson(HelpAnswer answer) {
    Object payload =
        switch (answer) {
          case PreparationStepExplanation explanation -> toPayload(explanation);
          case Substitutes substitutes -> toPayload(substitutes);
        };
    try {
      return new AnswerJson(AsyncApiJson.MAPPER.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write the answer as JSON", e);
    }
  }

  /** The answer in its AsyncAPI shape. */
  private static PreparationStepExplanationJson toPayload(PreparationStepExplanation answer) {
    return new PreparationStepExplanationJson(
        "PREPARATION_STEP_EXPLANATION",
        answer.recipe().value(),
        answer.howToStep().value(),
        answer.description(),
        answer.images());
  }

  private static SubstitutesJson toPayload(Substitutes answer) {
    return new SubstitutesJson(
        INGREDIENT_SUBSTITUTE,
        answer.recipe().value(),
        answer.substitute().stream().map(AnswerJsonMapper::toPayload).toList());
  }

  private static SubstituteJson toPayload(Substitute substitute) {
    return new SubstituteJson(
        substitute.ingredient().value(),
        substitute.substituteIngredients().stream().map(AnswerJsonMapper::toPayload).toList());
  }

  private static SubstituteIngredientJson toPayload(SubstituteIngredient ingredient) {
    return new SubstituteIngredientJson(
        ingredient.name(), ingredient.value(), ingredient.unit().name());
  }

  /** The answer stored as this JSON; its answerType says which kind it is. */
  static HelpAnswer toAnswer(AnswerJson json) {
    try {
      return INGREDIENT_SUBSTITUTE.equals(answerTypeOf(json))
          ? toSubstitutes(AsyncApiJson.MAPPER.readValue(json.value(), SubstitutesJson.class))
          : toExplanation(
              AsyncApiJson.MAPPER.readValue(json.value(), PreparationStepExplanationJson.class));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read the stored answer", e);
    }
  }

  private static String answerTypeOf(AnswerJson json) throws JsonProcessingException {
    return AsyncApiJson.MAPPER.readTree(json.value()).get("answerType").asText();
  }

  private static PreparationStepExplanation toExplanation(PreparationStepExplanationJson stored) {
    return new PreparationStepExplanation(
        new RecipeId(stored.recipe()),
        new HowToStepId(stored.howToStep()),
        stored.description(),
        stored.images());
  }

  private static Substitutes toSubstitutes(SubstitutesJson stored) {
    return new Substitutes(
        new RecipeId(stored.recipe()),
        stored.substitute().stream().map(AnswerJsonMapper::toSubstitute).toList());
  }

  private static Substitute toSubstitute(SubstituteJson stored) {
    return new Substitute(
        new IngredientId(stored.ingredient()),
        stored.substituteIngredients().stream()
            .map(AnswerJsonMapper::toSubstituteIngredient)
            .toList());
  }

  private static SubstituteIngredient toSubstituteIngredient(SubstituteIngredientJson stored) {
    return new SubstituteIngredient(stored.name(), stored.value(), Unit.valueOf(stored.unit()));
  }

  /** AsyncAPI shape of a Preparation Step Explanation answer. */
  record PreparationStepExplanationJson(
      String answerType, UUID recipe, UUID howToStep, String description, List<URI> images) {}

  /** AsyncAPI shape of a Substitutes answer. */
  record SubstitutesJson(String answerType, UUID recipe, List<SubstituteJson> substitute) {}

  /** AsyncAPI shape of one substitute. */
  record SubstituteJson(UUID ingredient, List<SubstituteIngredientJson> substituteIngredients) {}

  /** AsyncAPI shape of a substitute ingredient, with the amount kept exact. */
  record SubstituteIngredientJson(String name, BigDecimal value, String unit) {}
}
