package org.larder.cookingassistance.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.in.messaging.GrandmaHelpProvidedConsumer;
import org.larder.cookingassistance.adapter.in.messaging.Outcome;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Wiring through the real adapters and PostgreSQL: request or message in -> rows and outbox entries
 * out.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, TestSigningKeys.class})
class InboundWiringIT {

  static final String LENA_AS_COOK =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired ObjectMapper json;
  @Autowired ApplicationContext context;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  /** Lena asks the given provider about step 4 through the API; returns the new request's id. */
  private UUID lenaRequestsHelpFrom(String provider) throws Exception {
    String body =
        """
        {
          "title": "Sauce splits",
          "type": "PREPARATION_STEP_EXPLANATION",
          "description": "My hollandaise splits at step 4",
          "recipe": "%s",
          "howToStep": "%s",
          "preferredProvider": ["%s"]
        }
        """
            .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value(), provider);
    String response =
        mvc.perform(
                post("/help-requests")
                    .header(AUTHORIZATION, LENA_AS_COOK)
                    .header("version", "1.0.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(response).get("helpRequestId").asText());
  }

  private int rows(String table, UUID helpRequestId) {
    String column = table.equals("help_request") ? "id" : "help_request_id";
    return jdbc.sql("select count(*) from " + table + " where " + column + " = :id")
        .param("id", helpRequestId)
        .query(Integer.class)
        .single();
  }

  private int outboxRows(String eventType, UUID helpRequestId) {
    return jdbc.sql("select count(*) from outbox where event_type = :type and aggregate_id = :id")
        .param("type", eventType)
        .param("id", helpRequestId)
        .query(Integer.class)
        .single();
  }

  @DisplayName(
      "POST /help-requests with a signed token -> help_request row and outbox HelpRequestCreated -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void createHelpRequestStoresRowAndOutboxEntry() throws Exception {
    UUID helpRequestId = lenaRequestsHelpFrom("COMMUNITY");

    assertThat(rows("help_request", helpRequestId)).isEqualTo(1);
    assertThat(outboxRows("HelpRequestCreated", helpRequestId)).isEqualTo(1);
  }

  @DisplayName(
      "POST /help-requests/{id}/helps -> help row and outbox HelpProvided -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void provideHelpStoresHelpAndOutboxEntry() throws Exception {
    UUID helpRequestId = lenaRequestsHelpFrom("COMMUNITY");
    String jonasAnswer =
        """
        {
          "answerTitle": "Warm the bowl",
          "helpProviderType": "COMMUNITY",
          "answer": {
            "answerType": "PREPARATION_STEP_EXPLANATION",
            "recipe": "%s",
            "howToStep": "%s",
            "description": "Add the butter slowly to a warm bowl"
          }
        }
        """
            .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());

    mvc.perform(
            post("/help-requests/" + helpRequestId + "/helps")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.JONAS)
                        .roles("cook", "community")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jonasAnswer))
        .andExpect(status().isCreated());

    assertThat(rows("help", helpRequestId)).isEqualTo(1);
    assertThat(outboxRows("HelpProvided", helpRequestId)).isEqualTo(1);
  }

  @DisplayName(
      "Grandma message -> help row, processed_message row and outbox HelpProvided -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void grandmaMessageStoresHelpProcessedMessageAndOutboxEntry() throws Exception {
    UUID helpRequestId = lenaRequestsHelpFrom("GRANDMA_AVATAR");
    UUID messageId = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");
    String grandmasAnswer =
        """
        {
          "helpRequest": "%s",
          "answerTitle": "Butter in a thin stream",
          "answer": {
            "answerType": "PREPARATION_STEP_EXPLANATION",
            "recipe": "%s",
            "howToStep": "%s",
            "description": "Add the butter in a thin stream"
          }
        }
        """
            .formatted(
                helpRequestId, HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());

    Outcome outcome =
        context
            .getBean(GrandmaHelpProvidedConsumer.class)
            .handle(
                Map.of(
                    "correlationId", "0b8f7a52-4bfa-4d4e-9d6d-1f4b9c8f9a10",
                    "messageId", messageId.toString(),
                    "source", "grandma-avatar"),
                grandmasAnswer.getBytes(StandardCharsets.UTF_8));

    assertThat(outcome).isEqualTo(Outcome.ACK);
    assertThat(rows("help", helpRequestId)).isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from processed_message where message_id = :id")
                .param("id", messageId)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
    assertThat(outboxRows("HelpProvided", helpRequestId)).isEqualTo(1);
  }
}
