package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.larder.quality.ServiceChannels.CANCELLED;
import static org.larder.quality.ServiceChannels.CREATED;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * QS-04 (CI job, quality.yml): commands go on while the broker is away; events follow in order once
 * it is back. Scaled: a 60 s outage instead of 10 min, on test containers (indicative).
 */
@Tag("fault-injection")
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class BrokerOutageIT {

  static final String IN_ORDER = "quality.outage.in-order";

  @Value("${local.server.port}")
  int port;

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;
  @Autowired RabbitMQContainer broker;

  @DisplayName(
      "E5 broker paused about 60 s -> commands answered 201, events kept, published in order per help request within 5 s after the broker returns -- B3-S01 US-CA-03 QS-04 ADR0015")
  @Test
  void eventsFollowAfterTheOutage() throws Exception {
    ServiceChannels.listenToEvery(admin);
    // one queue for both channels: it receives the request's messages in publish order
    admin.declareQueue(new Queue(IN_ORDER, false, false, false));
    for (String channel : List.of(CREATED, CANCELLED)) {
      admin.declareBinding(
          BindingBuilder.bind(new Queue(IN_ORDER))
              .to(new TopicExchange("cooking-assistance"))
              .with(channel));
    }
    admin.purgeQueue(IN_ORDER, false);

    broker.getDockerClient().pauseContainerCmd(broker.getContainerId()).exec();
    ResponseEntity<String> created;
    ResponseEntity<String> cancelled;
    try {
      created = lenaAsks();
      String id = new ObjectMapper().readTree(created.getBody()).get("helpRequestId").asText();
      cancelled = lenaCancels(id);
      Thread.sleep(Duration.ofSeconds(60)); // the outage itself, not a wait for a result
    } finally {
      broker.getDockerClient().unpauseContainerCmd(broker.getContainerId()).exec();
    }
    long back = System.nanoTime();
    String id = new ObjectMapper().readTree(created.getBody()).get("helpRequestId").asText();
    String first = nextFor(id, Duration.ofSeconds(90));
    String second = nextFor(id, Duration.ofSeconds(90));
    Duration publishedAfterReturn = Duration.ofNanos(System.nanoTime() - back);
    System.out.println("E5 published after the broker returned: " + publishedAfterReturn);

    assertThat(created.getStatusCode().value())
        .as("create while the broker is away")
        .isEqualTo(201);
    assertThat(cancelled.getStatusCode().value())
        .as("cancel while the broker is away")
        .isEqualTo(200);
    assertThat(List.of(first, second))
        .as("the request's messages in publish order")
        .containsExactly(CREATED, CANCELLED);
    assertThat(publishedAfterReturn)
        .as("both published after the broker returned")
        .isLessThanOrEqualTo(Duration.ofSeconds(5));
  }

  private ResponseEntity<String> lenaAsks() {
    return client()
        .post()
        .uri("/help-requests")
        .header("Authorization", lenasToken())
        .header("version", "1.0.9")
        .contentType(MediaType.APPLICATION_JSON)
        .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY"))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<String> lenaCancels(String helpRequestId) {
    return client()
        .post()
        .uri("/help-requests/" + helpRequestId + "/cancellation")
        .header("Authorization", lenasToken())
        .header("version", "1.0.9")
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  /** Message ids already read: at-least-once delivery, consumers deduplicate (ADR0015). */
  private final java.util.Set<String> seen = new java.util.HashSet<>();

  /**
   * The channel (routing key) of the request's next new message in the ordered queue; a copy with a
   * message id already read is skipped, as a consumer would.
   */
  private String nextFor(String helpRequestId, Duration within) throws Exception {
    long deadline = System.nanoTime() + within.toNanos();
    while (System.nanoTime() < deadline) {
      Message message = rabbit.receive(IN_ORDER, 500);
      if (message != null) {
        var payload = new ObjectMapper().readTree(message.getBody());
        String request =
            payload.has("helpRequest")
                ? payload.get("helpRequest").asText()
                : payload.path("helpRequestId").asText();
        String messageId =
            String.valueOf(message.getMessageProperties().getHeaders().get("messageId"));
        if (request.equals(helpRequestId) && !seen.add(messageId)) {
          System.out.println("E5 duplicate skipped: " + messageId);
        } else if (request.equals(helpRequestId)) {
          return message.getMessageProperties().getReceivedRoutingKey();
        }
      }
    }
    throw new AssertionError("no further message for " + helpRequestId + " within " + within);
  }

  private RestClient client() {
    return RestClient.create("http://localhost:" + port);
  }

  private static String lenasToken() {
    return TestTokens.forCook(HelpRequests.LENA)
        .roles("cook")
        .scope("help:read help:write")
        .bearer();
  }
}
