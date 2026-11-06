package org.larder.acceptance;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.larder.cookingassistance.adapter.messaging.AsyncApiContract;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Talks to the running service only through its contracts: HTTP requests and messages on channel
 * addresses. Every message it reads is validated against the AsyncAPI spec.
 */
public class AcceptanceDriver {

  static final String CREATED = "cooking-assistance.help-request.created";
  static final String PROVIDED = "cooking-assistance.help.provided";
  static final String ACCEPTED = "cooking-assistance.help.accepted";
  static final String REJECTED = "cooking-assistance.help.rejected";
  static final String CANCELLED = "cooking-assistance.help-request.cancelled";
  static final String TIMED_OUT = "cooking-assistance.help-request.timed-out";
  static final String DEAD_LETTERS = "cooking-assistance.dlq";

  private final AmqpAdmin admin;
  private final RabbitTemplate rabbit;
  private final AsyncApiContract contract = new AsyncApiContract();
  private final ObjectMapper json = new ObjectMapper();
  private final int port;

  AcceptanceDriver(AmqpAdmin admin, RabbitTemplate rabbit, int port) {
    this.admin = admin;
    this.rabbit = rabbit;
    this.port = port;
  }

  /** A queue the test owns, bound to the service's exchange, emptied for each scenario. */
  String listenTo(String channel) {
    String queue = "acceptance." + channel;
    // not auto-deleted: a receive with a timeout subscribes and leaves, which would delete it
    admin.declareQueue(new Queue(queue, false, false, false));
    admin.declareBinding(
        BindingBuilder.bind(new Queue(queue))
            .to(new TopicExchange("cooking-assistance"))
            .with(channel));
    admin.purgeQueue(queue, false);
    return queue;
  }

  ResponseEntity<String> post(String path, CookId cook, String roles, String body) {
    return send(HttpMethod.POST, path, cook, roles, body);
  }

  ResponseEntity<String> get(String path, CookId cook, String roles) {
    return send(HttpMethod.GET, path, cook, roles, null);
  }

  /** Any request as the cook, with both scopes; error statuses are returned, not thrown. */
  ResponseEntity<String> send(
      HttpMethod method, String path, CookId cook, String roles, String body) {
    RestClient.RequestBodySpec request =
        RestClient.create("http://localhost:" + port)
            .method(method)
            .uri(path)
            .header(
                "Authorization",
                TestTokens.forCook(cook)
                    .roles(roles.split(","))
                    .scope("help:read help:write")
                    .bearer())
            .header("version", "1.0.4");
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return request
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, response) -> {})
        .toEntity(String.class);
  }

  JsonNode json(ResponseEntity<String> response) {
    return read(response.getBody().getBytes(UTF_8));
  }

  /** The test plays the Grandma Avatar context: it owns the grandma-avatar exchange. */
  void grandmaPublishes(String payload, String messageId) {
    admin.declareExchange(new TopicExchange("grandma-avatar", true, false));
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    properties.setHeader("correlationId", "0b8f7a52-4bfa-4d4e-9d6d-1f4b9c8f9a10");
    properties.setHeader("messageId", messageId);
    properties.setHeader("source", "grandma-avatar");
    rabbit.send(
        "grandma-avatar",
        "grandma-avatar.help.provided",
        new Message(payload.getBytes(UTF_8), properties));
  }

  /** The dead-letter queue of the service (ADR0015), emptied for each scenario. */
  void emptyDeadLetters() {
    admin.purgeQueue(DEAD_LETTERS, false);
  }

  /** The next dead-lettered message within the deadline. */
  String nextDeadLetter() {
    Message message = rabbit.receive(DEAD_LETTERS, 10_000);
    if (message == null) {
      throw new AssertionError("nothing dead-lettered within 10 s");
    }
    return new String(message.getBody(), UTF_8);
  }

  /** No message on the channel: the only fixed wait of the suite, since absence has no event. */
  void noMessage(String queue) {
    Message message = rabbit.receive(queue, 2_000);
    if (message != null) {
      throw new AssertionError("unexpected message: " + new String(message.getBody(), UTF_8));
    }
  }

  /** The next message on a channel within the deadline, validated against its AsyncAPI payload. */
  JsonNode nextMessage(String queue, String messageName) {
    Message message = rabbit.receive(queue, 10_000);
    if (message == null) {
      throw new AssertionError("no " + messageName + " within 10 s");
    }
    JsonNode payload = read(message.getBody());
    List<String> violations =
        contract.violations(contract.payloadSchemaOf(messageName).orElseThrow(), payload);
    if (!violations.isEmpty()) {
      throw new AssertionError(messageName + " breaks the AsyncAPI spec: " + violations);
    }
    return payload;
  }

  private JsonNode read(byte[] body) {
    try {
      return json.readTree(body);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
