package org.larder.cookingassistance.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
  @Autowired Clock clock;
  @Autowired ObjectMapper json;
  @Autowired ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp;

  final RecordingPublisher publisher = new RecordingPublisher();

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  /** Lena asks the Community about step 4 through the API; returns the new request's id. */
  private UUID lenaRequestsHelp() throws Exception {
    String response =
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
                            .formatted(
                                HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(response).get("helpRequestId").asText());
  }

  @DisplayName(
      "POST /help-requests -> one relay run -> the fake received exactly one HelpRequestCreated on cooking-assistance.help-request.created; no pending rows -- B3-S01 US-CA-03 op publishHelpRequestCreated")
  @Test
  void createdHelpRequestReachesItsChannel() throws Exception {
    lenaRequestsHelp();

    new OutboxRelay(jdbc, transactions, publisher, clock).runOnce();

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

  @DisplayName(
      "W2 POST /help-requests, then POST .../cancellation by Lena -> one relay run -> the fake received HelpRequestCreated then exactly one HelpRequestCancelled on cooking-assistance.help-request.cancelled; no pending rows -- B3-S03 US-CA-03 op publishHelpRequestCancelled wiring")
  @Test
  void cancellationReachesItsChannel() throws Exception {
    UUID helpRequestId = lenaRequestsHelp();
    mvc.perform(
            post("/help-requests/" + helpRequestId + "/cancellation")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"an ice cube saved it\"}"))
        .andExpect(status().isOk());

    new OutboxRelay(jdbc, transactions, publisher, clock).runOnce();

    assertThat(publisher.published())
        .extracting(OutgoingMessage::channel)
        .containsExactly(
            "cooking-assistance.help-request.created", "cooking-assistance.help-request.cancelled");
    assertThat(publisher.published().getLast().headers().get("correlationId"))
        .isEqualTo(helpRequestId.toString());
    assertThat(
            jdbc.sql("select count(*) from outbox where published_at is null")
                .query(Integer.class)
                .single())
        .isZero();
  }

  @DisplayName(
      "RequestHelpWithIngredient through the real adapters, then one relay run -> one message on the created channel with ingredient and substituteRequirements, no pending rows -- B2-S01 US-CA-02 wiring")
  @Test
  void ingredientRequestReachesItsChannel() throws Exception {
    lenaAsksForASubstitute("COMMUNITY");

    new OutboxRelay(jdbc, transactions, publisher, clock).runOnce();

    assertThat(publisher.published())
        .singleElement()
        .satisfies(
            message -> {
              assertThat(message.channel()).isEqualTo("cooking-assistance.help-request.created");
              JsonNode payload = json.readTree(message.payload());
              assertThat(payload.get("ingredient").asText())
                  .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
              assertThat(payload.get("substituteRequirements").asText())
                  .isEqualTo("200 g, not available");
            });
    assertNothingPending();
  }

  @DisplayName(
      "Grandma's substitutes saved through the real adapters, then one relay run -> one message on the provided channel with the substitute, no pending rows -- B2-S02 US-CA-02 wiring")
  @Test
  void substitutesReachTheirChannel() throws Exception {
    UUID helpRequestId = lenaAsksForASubstitute("GRANDMA_AVATAR");
    provideGrandmaAvatarHelp.handle(
        new ProvideGrandmaAvatarHelp.GrandmaAnswer(
            UUID.fromString("7182a3b4-b5c6-4d7e-8f01-6c7d8e9fa0b1"),
            new HelpRequestId(helpRequestId),
            "Yogurt",
            new Substitutes(
                HelpRequests.TOMATO_SOUP, List.of(HelpRequests.gramsOf("yogurt", "200")))));

    new OutboxRelay(jdbc, transactions, publisher, clock).runOnce();

    assertThat(publisher.published())
        .extracting(OutgoingMessage::channel)
        .containsExactly(
            "cooking-assistance.help-request.created", "cooking-assistance.help.provided");
    JsonNode answer = json.readTree(publisher.published().getLast().payload()).get("answer");
    assertThat(
            answer
                .get("substitute")
                .get(0)
                .get("substituteIngredients")
                .get(0)
                .get("name")
                .asText())
        .isEqualTo("yogurt");
    assertNothingPending();
  }

  /** Lena asks for a substitute for creme fraiche through the API; returns the new request's id. */
  private UUID lenaAsksForASubstitute(String provider) throws Exception {
    String response =
        mvc.perform(
                post("/help-requests")
                    .header(
                        AUTHORIZATION,
                        TestTokens.forCook(HelpRequests.LENA)
                            .roles("cook")
                            .scope("help:write")
                            .bearer())
                    .header("version", "1.0.13")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                    {
                      "title": "No creme fraiche",
                      "type": "INGREDIENT_SUBSTITUTE",
                      "description": "The shop has none",
                      "recipe": "%s",
                      "ingredient": "%s",
                      "substituteRequirements": "200 g, not available",
                      "preferredProvider": ["%s"]
                    }
                    """
                            .formatted(
                                HelpRequests.TOMATO_SOUP.value(),
                                HelpRequests.CREME_FRAICHE.value(),
                                provider)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(json.readTree(response).get("helpRequestId").asText());
  }

  private void assertNothingPending() {
    assertThat(
            jdbc.sql("select count(*) from outbox where published_at is null")
                .query(Integer.class)
                .single())
        .isZero();
  }
}
