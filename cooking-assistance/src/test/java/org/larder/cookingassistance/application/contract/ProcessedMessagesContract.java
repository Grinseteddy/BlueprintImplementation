package org.larder.cookingassistance.application.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.ProcessedMessages;

/** The ProcessedMessages contract (step 7), run against the fake and the PostgreSQL adapter. */
public abstract class ProcessedMessagesContract {

  /** The store under test, empty at the start of each test. */
  protected abstract ProcessedMessages processedMessages();

  static final UUID MESSAGE_ID = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");

  @DisplayName("a message id seen for the first time -> true -- B3-S02 US-CA-03 ADR0015 contract")
  @Test
  void firstProcessingIsRecorded() {
    assertThat(processedMessages().recordFirstProcessing(MESSAGE_ID)).isTrue();
  }

  @DisplayName("the same message id again -> false, no error -- B3-S02 US-CA-03 ADR0015 contract")
  @Test
  void repeatedMessageIsReportedAsAlreadyProcessed() {
    processedMessages().recordFirstProcessing(MESSAGE_ID);

    assertThat(processedMessages().recordFirstProcessing(MESSAGE_ID)).isFalse();
  }
}
