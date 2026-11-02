package org.larder.cookingassistance.adapter.in.messaging;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.larder.cookingassistance.adapter.messaging.generated.Answer;
import org.larder.cookingassistance.adapter.messaging.generated.GrandmaHelpProvidedPayload;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp.GrandmaAnswer;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/**
 * Anticorruption mapping at the border to the Grandma Avatar context: its message in our language
 * (their helpRequest reference becomes our HelpRequestId).
 */
final class GrandmaAnswerMapper {

  private GrandmaAnswerMapper() {}

  static GrandmaAnswer toGrandmaAnswer(UUID messageId, GrandmaHelpProvidedPayload payload) {
    return new GrandmaAnswer(
        messageId,
        new HelpRequestId(payload.helpRequest()),
        payload.answerTitle(),
        toAnswer(payload.answer()));
  }

  /** Only Preparation Step Explanations are built yet; the other answer kinds come later. */
  private static PreparationStepExplanation toAnswer(Answer answer) {
    return switch (answer) {
      case org.larder.cookingassistance.adapter.messaging.generated.PreparationStepExplanation
              explanation ->
          new PreparationStepExplanation(
              new RecipeId(explanation.recipe()),
              new HowToStepId(explanation.howToStep()),
              explanation.description(),
              // images are optional in the spec: no images, no pictures
              Objects.requireNonNullElse(explanation.images(), List.<String>of()).stream()
                  .map(GrandmaAnswerMapper::pictureLink)
                  .toList());
      default -> throw new AnswerKindNotBuilt(answer.getClass().getSimpleName());
    };
  }

  /**
   * A link the schema lets through but that is no URI breaks the contract; retrying cannot fix it.
   */
  private static URI pictureLink(String link) {
    try {
      return new URI(link);
    } catch (URISyntaxException notAUri) {
      throw new InvalidMessage(List.of("images: " + link + " is not a URI"));
    }
  }
}
