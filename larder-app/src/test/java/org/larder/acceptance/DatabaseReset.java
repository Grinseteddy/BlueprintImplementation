package org.larder.acceptance;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Only a test's own setup touches the database: it starts from empty tables. */
public final class DatabaseReset {

  private DatabaseReset() {}

  /**
   * One statement empties all tables at once. A poller's transaction that reads one table and then
   * another can deadlock with it; PostgreSQL then cancels the truncate, which is tried again.
   */
  public static void emptyAllTables(JdbcClient jdbc) {
    for (int attempt = 1; ; attempt++) {
      try {
        jdbc.sql(
                "truncate cooking_assistance.help_request_note, cooking_assistance.help,"
                    + " cooking_assistance.asked_provider, cooking_assistance.help_request,"
                    + " cooking_assistance.outbox, cooking_assistance.processed_message")
            .update();
        return;
      } catch (PessimisticLockingFailureException deadlock) {
        if (attempt == 3) {
          throw deadlock;
        }
      }
    }
  }
}
