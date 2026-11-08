package org.larder.cookingassistance.application;

import java.time.Clock;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;

// cmd: Provide Help (Grandma Avatar, via GrandmaHelpProvided) · agg: Help Request · slice: B3-S02,
// story: US-CA-03
/**
 * Handles the Grandma Avatar's answers from the broker as the service's own identity: idempotent by
 * message id, record and save in one unit of work (ADR0015).
 */
public class ProvideGrandmaAvatarHelp {

  private final HelpRequestRepository helpRequests;
  private final ProcessedMessages processedMessages;
  private final UnitOfWork unitOfWork;
  private final IdGenerator ids;
  private final Clock clock;

  public ProvideGrandmaAvatarHelp(
      HelpRequestRepository helpRequests,
      ProcessedMessages processedMessages,
      UnitOfWork unitOfWork,
      IdGenerator ids,
      Clock clock) {
    this.helpRequests = helpRequests;
    this.processedMessages = processedMessages;
    this.unitOfWork = unitOfWork;
    this.ids = ids;
    this.clock = clock;
  }

  public void handle(GrandmaAnswer answer) {
    // ADR0010: another writer saved first — retry once in a new unit of work
    RetryOnConflict.once(() -> handleOnce(answer));
  }

  private void handleOnce(GrandmaAnswer answer) {
    // ADR0015: record and save in one unit of work
    unitOfWork.transactionally(
        () -> {
          provideHelpIfFirstDelivery(answer);
          return null;
        });
  }

  private void provideHelpIfFirstDelivery(GrandmaAnswer answer) {
    // ADR0015: a redelivered message has no effect
    if (!processedMessages.recordFirstProcessing(answer.messageId())) {
      return;
    }
    HelpRequest request =
        helpRequests
            .findById(answer.helpRequestId())
            .orElseThrow(() -> new HelpRequestNotFound(answer.helpRequestId()));
    request.provideHelp(
        new HelpId(ids.next()),
        HelpProvider.grandmaAvatar(),
        answer.answerTitle(),
        answer.answer(),
        clock);
    helpRequests.save(request);
  }

  /** The Grandma Avatar's answer in the context's own language; step 9 maps the message to it. */
  public record GrandmaAnswer(
      UUID messageId,
      HelpRequestId helpRequestId,
      String answerTitle,
      PreparationStepExplanation answer) {}
}
