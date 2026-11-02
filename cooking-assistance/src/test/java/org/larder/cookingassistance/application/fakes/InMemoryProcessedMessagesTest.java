package org.larder.cookingassistance.application.fakes;

import org.larder.cookingassistance.application.ProcessedMessages;
import org.larder.cookingassistance.application.contract.ProcessedMessagesContract;

/** Runs the processed-messages contract against the in-memory fake (fast). */
class InMemoryProcessedMessagesTest extends ProcessedMessagesContract {

  private final InMemoryProcessedMessages processedMessages = new InMemoryProcessedMessages();

  @Override
  protected ProcessedMessages processedMessages() {
    return processedMessages;
  }
}
