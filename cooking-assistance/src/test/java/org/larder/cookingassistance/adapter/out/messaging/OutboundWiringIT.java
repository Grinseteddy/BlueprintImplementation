package org.larder.cookingassistance.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wiring: command through HTTP, use case and repository, then one relay run -> the promised
 * message.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, TestSigningKeys.class})
class OutboundWiringIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate transactions;

  final RecordingPublisher publisher = new RecordingPublisher();

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @DisplayName(
      "POST /help-requests -> one relay run -> the fake received exactly one HelpRequestCreated on cooking-assistance.help-request.created; no pending rows -- B3-S01 US-CA-03 op publishHelpRequestCreated")
  @Test
  void createdHelpRequestReachesItsChannel() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "title": "Sauce splits",
                      "type": "PREPARATION_STEP_EXPLANATION",
                      "description": "My hollandaise splits at step 4",
                      "recipe": "%s",
                      "howToStep": "%s",
                      "preferredProvider": ["COMMUNITY"]
                    }
                    """
                        .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value())))
        .andExpect(status().isCreated());

    new OutboxRelay(jdbc, transactions, publisher).runOnce();

    assertThat(publisher.published())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message.channel()).isEqualTo("cooking-assistance.help-request.created"));
    assertThat(
            jdbc.sql("select count(*) from outbox where published_at is null")
                .query(Integer.class)
                .single())
        .isZero();
  }
}
