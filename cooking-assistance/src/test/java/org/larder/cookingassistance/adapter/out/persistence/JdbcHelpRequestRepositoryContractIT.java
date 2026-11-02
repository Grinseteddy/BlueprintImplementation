package org.larder.cookingassistance.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.larder.cookingassistance.application.contract.HelpRequestRepositoryContract;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Runs the repository contract against the PostgreSQL adapter (slow). */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class JdbcHelpRequestRepositoryContractIT extends HelpRequestRepositoryContract {

  @Autowired HelpRequestRepository repository;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @Override
  protected HelpRequestRepository repository() {
    return repository;
  }

  @Override
  protected int recordedEventCount() {
    return jdbc.sql("select count(*) from outbox").query(Integer.class).single();
  }
}
