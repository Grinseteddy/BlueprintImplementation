package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.UnitOfWork;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Unit of work against PostgreSQL 16 (Testcontainers) — increment 0, walking skeleton. Needed
 * because the skeleton's consumer writes the processed-message record and the Help Request in one
 * transaction (ADR0015).
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class UnitOfWorkIT {

  static final UUID MESSAGE_ID = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");

  @Autowired UnitOfWork unitOfWork;
  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository repository;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @DisplayName(
      "work done inside transactionally(...) is committed when it returns: a processed-message record written inside is visible afterwards -- B3-S02 US-CA-03 ADR0015 unit of work")
  @Test
  void workIsCommittedWhenItReturns() {
    unitOfWork.transactionally(() -> insertProcessedMessage(MESSAGE_ID));

    assertThat(processedMessageCount(MESSAGE_ID)).isEqualTo(1);
  }

  private int insertProcessedMessage(UUID messageId) {
    return jdbc.sql("insert into processed_message (message_id) values (:id)")
        .param("id", messageId)
        .update();
  }

  private int processedMessageCount(UUID messageId) {
    return jdbc.sql("select count(*) from processed_message where message_id = :id")
        .param("id", messageId)
        .query(Integer.class)
        .single();
  }

  @DisplayName(
      "work is rolled back when it throws: a processed-message record written inside is not visible afterwards and the exception reaches the caller -- B3-S02 US-CA-03 ADR0015 unit of work")
  @Test
  void workIsRolledBackWhenItThrows() {
    IllegalStateException failure = new IllegalStateException("work failed");

    assertThatThrownBy(
            () ->
                unitOfWork.transactionally(
                    () -> {
                      insertProcessedMessage(MESSAGE_ID);
                      throw failure;
                    }))
        .isSameAs(failure);
    assertThat(processedMessageCount(MESSAGE_ID)).isZero();
  }

  @DisplayName(
      "a HelpRequestRepository.save inside transactionally(...) joins its transaction: save, then throw -> neither the help_request row nor its outbox row exists -- B3-S02 US-CA-03 ADR0015 unit of work")
  @Test
  void repositorySaveJoinsTheUnitOfWork() {
    assertThatThrownBy(
            () ->
                unitOfWork.transactionally(
                    () -> {
                      repository.save(
                          HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
                      throw new IllegalStateException("work failed after the save");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(jdbc.sql("select count(*) from help_request").query(Integer.class).single())
        .isZero();
    assertThat(jdbc.sql("select count(*) from outbox").query(Integer.class).single()).isZero();
  }
}
