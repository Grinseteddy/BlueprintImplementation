package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.ProcessedMessages;
import org.larder.cookingassistance.application.UnitOfWork;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Processed-message record for idempotent consumers against PostgreSQL 16 (Testcontainers) —
 * increment 0, walking skeleton: the consumer of Grandma Avatar's answers (ADR0015, QS-04).
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class ProcessedMessagesIT {

  static final UUID MESSAGE_ID = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");

  @Autowired ProcessedMessages processedMessages;
  @Autowired UnitOfWork unitOfWork;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void emptyTable() {
    jdbc.sql("delete from processed_message").update();
  }

  @DisplayName(
      "recording a message id seen for the first time returns true and stores it -- B3-S02 US-CA-03 ADR0015 consumer idempotency")
  @Test
  void firstProcessingIsRecorded() {
    boolean first = processedMessages.recordFirstProcessing(MESSAGE_ID);

    assertThat(first).isTrue();
    assertThat(storedCount(MESSAGE_ID)).isEqualTo(1);
  }

  private int storedCount(UUID messageId) {
    return jdbc.sql("select count(*) from processed_message where message_id = :id")
        .param("id", messageId)
        .query(Integer.class)
        .single();
  }

  @DisplayName(
      "recording the same message id again returns false (already processed), without an error -- B3-S02 US-CA-03 ADR0015 consumer idempotency")
  @Test
  void repeatedMessageIsReportedAsAlreadyProcessed() {
    processedMessages.recordFirstProcessing(MESSAGE_ID);

    boolean again = processedMessages.recordFirstProcessing(MESSAGE_ID);

    assertThat(again).isFalse();
    assertThat(storedCount(MESSAGE_ID)).isEqualTo(1);
  }

  @DisplayName(
      "a record written inside a unit of work that rolls back is not kept: the same message id later counts as first processing again -- B3-S02 US-CA-03 ADR0015 unit of work")
  @Test
  void recordIsRolledBackWithItsUnitOfWork() {
    assertThatThrownBy(
            () ->
                unitOfWork.transactionally(
                    () -> {
                      processedMessages.recordFirstProcessing(MESSAGE_ID);
                      throw new IllegalStateException("processing failed");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(processedMessages.recordFirstProcessing(MESSAGE_ID)).isTrue();
  }
}
