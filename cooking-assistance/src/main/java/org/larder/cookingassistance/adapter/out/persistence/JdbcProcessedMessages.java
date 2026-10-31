package org.larder.cookingassistance.adapter.out.persistence;

import java.util.UUID;
import org.larder.cookingassistance.application.ProcessedMessages;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Processed-message records in table processed_message (ADR0015). */
@Repository
class JdbcProcessedMessages implements ProcessedMessages {

  private final JdbcClient jdbc;

  JdbcProcessedMessages(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean recordFirstProcessing(UUID messageId) {
    int inserted =
        jdbc.sql(
                "insert into processed_message (message_id) values (:id)"
                    + " on conflict (message_id) do nothing")
            .param("id", messageId)
            .update();
    return inserted == 1;
  }
}
