package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/** The query adapter reads one help request as one consistent snapshot (ADR0016, QS-07). */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class JdbcHelpRequestQueriesIT {

  @Autowired JdbcClient jdbc;
  @Autowired DataSource dataSource;
  @Autowired HelpRequestRepository helpRequests;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @DisplayName(
      "Jonas's answer commits while Lena's request is being read -> one snapshot: status OPEN with COMMUNITY ASKED, never ANSWERED with ASKED -- B3-S02 US-CA-03 op getHelpRequestById ADR0016")
  @Test
  void aConcurrentAnswerDoesNotTearTheView() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    DataSource answeredAfterFirstStatement =
        afterFirstStatement(
            () -> {
              HelpRequest lenas = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
              HelpRequests.communityMemberExplainsStep4(
                  lenas, HelpRequests.HELP_ID, HelpRequests.JONAS);
              helpRequests.save(lenas);
            });
    JdbcHelpRequestQueries queries =
        new JdbcHelpRequestQueries(
            JdbcClient.create(answeredAfterFirstStatement),
            new DataSourceTransactionManager(answeredAfterFirstStatement));

    HelpRequestView view = queries.byId(HelpRequests.HELP_REQUEST_ID).orElseThrow();

    assertThat(view.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(view.askedProviders())
        .extracting(AskedProvider::state)
        .containsExactly(ProviderState.ASKED);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .as("the answer did commit meanwhile")
        .isEqualTo(HelpRequestStatus.ANSWERED);
  }

  /** The data source, running another writer once, right after the first statement executed. */
  private DataSource afterFirstStatement(Runnable anotherWriter) {
    AtomicBoolean done = new AtomicBoolean();
    return proxy(
        DataSource.class,
        (source, method, args) -> {
          Object result = invoke(method, dataSource, args);
          if (!(result instanceof Connection connection)) {
            return result;
          }
          return proxy(
              Connection.class,
              (c, m, a) -> {
                Object made = invoke(m, connection, a);
                if (!(made instanceof PreparedStatement statement)) {
                  return made;
                }
                return proxy(
                    PreparedStatement.class,
                    (s, sm, sa) -> {
                      Object executed = invoke(sm, statement, sa);
                      if (sm.getName().startsWith("execute") && done.compareAndSet(false, true)) {
                        anotherWriter.run();
                      }
                      return executed;
                    });
              });
        });
  }

  private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
    try {
      return method.invoke(target, args);
    } catch (InvocationTargetException e) {
      throw e.getCause();
    }
  }

  private static <T> T proxy(Class<T> type, InvocationHandler handler) {
    return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
  }
}
