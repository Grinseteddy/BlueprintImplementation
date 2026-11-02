package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** Writes and reads a Help's answer as JSON under the AsyncAPI field names. */
final class AnswerJsonMapper {

  private AnswerJsonMapper() {}

  static AnswerJson toJson(PreparationStepExplanation answer) {
    try {
      return new AnswerJson(AsyncApiJson.MAPPER.writeValueAsString(toPayload(answer)));
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

  static PreparationStepExplanation toAnswer(AnswerJson json) {
    try {
      PreparationStepExplanationJson stored =
          AsyncApiJson.MAPPER.readValue(json.value(), PreparationStepExplanationJson.class);
      return new PreparationStepExplanation(
          new RecipeId(stored.recipe()),
          new HowToStepId(stored.howToStep()),
          stored.description(),
          stored.images());
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot read the stored answer", e);
    }
  }

  /** AsyncAPI shape of a Preparation Step Explanation answer. */
  record PreparationStepExplanationJson(
      String answerType, UUID recipe, UUID howToStep, String description, List<URI> images) {}
}
