package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

/** The trace context written with the outbox rows (ADR0020, inspection A-1). */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import({PostgresTestContainer.class, OutboxTraceparentIT.TestTrace.class})
class OutboxTraceparentIT {

  static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

  /** The current trace as the test sets it. */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestTrace {

    static final AtomicReference<String> CURRENT = new AtomicReference<>();

    @Bean
    @Primary
    CurrentTrace testTrace() {
      return () -> Optional.ofNullable(CURRENT.get());
    }
  }

  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository repository;

  @BeforeEach
  void emptyTablesAndNoTrace() {
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
    TestTrace.CURRENT.set(null);
  }

  private String storedTraceparent() {
    List<String> rows =
        jdbc.sql("select traceparent from outbox")
            .query((rs, n) -> rs.getString("traceparent"))
            .list();
    assertThat(rows).hasSize(1);
    return rows.getFirst();
  }

  @DisplayName(
      "T1 Lena's request saved inside trace 4bf92f35... -> its outbox row carries traceparent 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01 -- B3-S01 US-CA-03 ADR0020 QS-14")
  @Test
  void outboxRowCarriesTheCurrentTrace() {
    TestTrace.CURRENT.set(TRACEPARENT);

    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    assertThat(storedTraceparent()).isEqualTo(TRACEPARENT);
  }

  @DisplayName(
      "T2 Lena's request saved without a current trace -> its outbox row has no traceparent -- B3-S01 US-CA-03 ADR0020")
  @Test
  void outboxRowWithoutTraceHasNone() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    assertThat(storedTraceparent()).isNull();
  }
}
