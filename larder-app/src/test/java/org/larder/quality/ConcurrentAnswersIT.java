package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.larder.quality.ServiceChannels.PROVIDED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** QS-10: answers given at the same time are all stored (optimistic locking, one retry). */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class ConcurrentAnswersIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;

  @DisplayName(
      "E3 Jonas (HTTP) and Grandma Avatar (message) answer the same request at the same moment -> both Helps stored, two HelpProvided -- B3-S02 US-CA-03 QS-10 ADR0010")
  @Test
  void bothAnswersAreStored() throws Exception {
    ServiceChannels.listenToEvery(admin);
    String helpRequestId = lenaAsksTheCommunityAndGrandma();

    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<ResponseEntity<String>> jonas =
        CompletableFuture.supplyAsync(
            () -> {
              await(start);
              return jonasAnswers(helpRequestId);
            });
    CompletableFuture<Void> grandma =
        CompletableFuture.runAsync(
            () -> {
              await(start);
              grandmaAnswers(helpRequestId);
            });
    start.countDown();
    grandma.join();

    assertThat(jonas.join().getStatusCode().value()).as("Jonas's answer").isEqualTo(201);
    assertThat(helpProvidersEventually(helpRequestId, 2, Duration.ofSeconds(15)))
        .as("stored Helps")
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(providedFor(helpRequestId, 2, Duration.ofSeconds(15)))
        .as("HelpProvided messages")
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
  }

  private String lenaAsksTheCommunityAndGrandma() throws Exception {
    String created =
        client()
            .post()
            .uri("/help-requests")
            .header("Authorization", token(HelpRequests.LENA, "cook"))
            .header("version", "1.0.9")
            .contentType(MediaType.APPLICATION_JSON)
            .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY", "GRANDMA_AVATAR"))
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(created).get("helpRequestId").asText();
  }

  private ResponseEntity<String> jonasAnswers(String helpRequestId) {
    return client()
        .post()
        .uri("/help-requests/" + helpRequestId + "/helps")
        .header("Authorization", token(HelpRequests.JONAS, "cook,community"))
        .header("version", "1.0.9")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            """
            {"answerTitle":"Warm the bowl","helpProviderType":"COMMUNITY",
             "answer":{"answerType":"PREPARATION_STEP_EXPLANATION","recipe":"%s","howToStep":"%s",
             "description":"Add the butter slowly to a warm bowl"}}"""
                .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value()))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  /** The test plays the Grandma Avatar context, which owns the grandma-avatar exchange. */
  private void grandmaAnswers(String helpRequestId) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    properties.setHeader("correlationId", helpRequestId);
    properties.setHeader("messageId", java.util.UUID.randomUUID().toString());
    properties.setHeader("source", "grandma-avatar");
    String payload =
        """
        {"helpRequest":"%s","answerTitle":"Butter in a thin stream",
         "answer":{"answerType":"PREPARATION_STEP_EXPLANATION","recipe":"%s","howToStep":"%s",
         "description":"butter in a thin stream"}}"""
            .formatted(
                helpRequestId, HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());
    rabbit.send(
        "grandma-avatar",
        "grandma-avatar.help.provided",
        new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
  }

  /** The provider types of the request's stored Helps, once there are as many as expected. */
  private List<String> helpProvidersEventually(String helpRequestId, int count, Duration within)
      throws Exception {
    long deadline = System.nanoTime() + within.toNanos();
    List<String> providers = List.of();
    while (System.nanoTime() < deadline) {
      String page =
          client()
              .get()
              .uri("/help-requests/" + helpRequestId + "/helps")
              .header("Authorization", token(HelpRequests.LENA, "cook"))
              .header("version", "1.0.9")
              .retrieve()
              .body(String.class);
      providers =
          new ObjectMapper().readTree(page).get("items").findValuesAsText("helpProviderType");
      if (providers.size() >= count) {
        return providers;
      }
      Thread.sleep(200);
    }
    return providers;
  }

  /** The provider types of this request's HelpProvided messages, up to the expected count. */
  private List<String> providedFor(String helpRequestId, int count, Duration within)
      throws Exception {
    long deadline = System.nanoTime() + within.toNanos();
    List<String> providers = new ArrayList<>();
    while (System.nanoTime() < deadline && providers.size() < count) {
      Message message = rabbit.receive(ServiceChannels.queueOn(PROVIDED), 500);
      if (message != null) {
        JsonNode payload = new ObjectMapper().readTree(message.getBody());
        if (payload.get("helpRequest").asText().equals(helpRequestId)) {
          providers.add(payload.get("helpProviderType").asText());
        }
      }
    }
    return providers;
  }

  private RestClient client() {
    return RestClient.create("http://localhost:" + port);
  }

  private static String token(CookId cook, String roles) {
    return TestTokens.forCook(cook).roles(roles.split(",")).scope("help:read help:write").bearer();
  }

  private static void await(CountDownLatch start) {
    try {
      start.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
