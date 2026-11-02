package org.larder.cookingassistance.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.larder.cookingassistance.application.ProcessedMessages;
import org.larder.cookingassistance.application.contract.ProcessedMessagesContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs the processed-messages contract against the PostgreSQL adapter (slow). */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class JdbcProcessedMessagesContractIT extends ProcessedMessagesContract {

  @Autowired ProcessedMessages processedMessages;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void emptyTable() {
    jdbc.sql("delete from processed_message").update();
  }

  @Override
  protected ProcessedMessages processedMessages() {
    return processedMessages;
  }
}
