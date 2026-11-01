package org.larder.cookingassistance.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.in.messaging.GrandmaHelpProvidedConsumer;
import org.larder.cookingassistance.adapter.in.messaging.Outcome;
import org.larder.cookingassistance.adapter.out.messaging.MessagePublisher;
import org.larder.cookingassistance.adapter.out.messaging.OutgoingMessage;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.fixtures.Eventually;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The real AMQP adapters deferred from steps 9 and 10, against RabbitMQ (ADR0003, ADR0015).
 */
@SpringBootTest(
    classes = PersistenceTestApplication.class,
    properties = "cooking-assistance.outbox.poller.enabled=true")
@Import({PostgresTestContainer.class, RabbitTestContainer.class})
class AmqpAdaptersIT {

  static final String CREATED = "cooking-assistance.help-request.created";

  @Autowired MessagePublisher publisher;
  @Autowired RabbitTemplate rabbit;
  @Autowired AmqpAdmin admin;

  /** A queue the test owns, bound to the service's exchange. */
  private String probe(String routingKey) {
    Queue queue = admin.declareQueue();
    admin.declareBinding(
        BindingBuilder.bind(queue).to(new TopicExchange("cooking-assistance")).with(routingKey));
    return queue.getName();
  }

  static OutgoingMessage message(String channel) {
    UUID id = UUID.fromString("8c6894b6-9321-3924-ac9a-3d6f6d4c5aa7");
    return new OutgoingMessage(
        channel,
        id,
        Map.of(
            "messageId", id.toString(),
            "correlationId", "23a8eeed-35f6-460b-892e-7bb458a8fded",
            "source", "cooking-assistance"),
        "{\"helpRequestId\":\"23a8eeed-35f6-460b-892e-7bb458a8fded\"}");
  }

  @DisplayName("A1 publish -> on exchange cooking-assistance with routing key = channel address; body = payload, AMQP messageId, headers messageId/correlationId/source, content type application/json, persistent -- B3-S01 US-CA-03 ADR0015 op publishHelpRequestCreated")
  @Test
  void publisherPutsTheMessageOnItsChannel() {
    String queue = probe(CREATED);

    publisher.publish(message(CREATED));

    Message received = rabbit.receive(queue, 5_000);
    assertThat(received).isNotNull();
    assertThat(new String(received.getBody())).isEqualTo(message(CREATED).payload());
    assertThat(received.getMessageProperties().getMessageId())
        .isEqualTo("8c6894b6-9321-3924-ac9a-3d6f6d4c5aa7");
    assertThat(received.getMessageProperties().getHeaders())
        .containsEntry("messageId", "8c6894b6-9321-3924-ac9a-3d6f6d4c5aa7")
        .containsEntry("correlationId", "23a8eeed-35f6-460b-892e-7bb458a8fded")
        .containsEntry("source", "cooking-assistance");
    assertThat(received.getMessageProperties().getContentType()).isEqualTo("application/json");
    assertThat(received.getMessageProperties().getReceivedDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @DisplayName("A2 publish returns only after the broker confirmed; with the exchange missing it throws -- B3-S01 US-CA-03 ADR0015")
  @Test
  void publishReturnsOnlyAfterTheConfirm() {
    admin.deleteExchange("cooking-assistance");
    try {
      assertThatThrownBy(() -> publisher.publish(message(CREATED))).isInstanceOf(RuntimeException.class);
    } finally {
      admin.declareExchange(new TopicExchange("cooking-assistance", true, false));
    }
  }

  @DisplayName("A3 an unroutable message (no queue bound) throws (operation binding mandatory: true) -- B3-S01 US-CA-03 ADR0015 AsyncAPI persistentPublish")
  @Test
  void unroutableMessageIsRefused() {
    assertThatThrownBy(() -> publisher.publish(message("cooking-assistance.nobody-listens")))
        .isInstanceOf(RuntimeException.class);
  }

  @DisplayName("A4 a GrandmaHelpProvided message on exchange grandma-avatar, key grandma-avatar.help.provided -> consumer handles it, acked: queue empty, DLQ empty, help stored -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void receivedMessageIsHandedToTheConsumerAndAcked() {
    lenasRequestToGrandmaIsStored();

    grandmaAnswers(GRANDMAS_ANSWER, "1b2c3d4e-5f60-4718-8a9b-0c1d2e3f4a5b");

    Eventually.within(
        Duration.ofSeconds(10),
        () ->
            assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
                .hasSize(1));
    Eventually.within(
        Duration.ofSeconds(10), () -> assertThat(messageCount(GRANDMA_QUEUE)).isZero());
    assertThat(messageCount(DEAD_LETTER_QUEUE)).isZero();
  }

  static final String GRANDMA_QUEUE = "cooking-assistance.grandma-help-provided";
  static final String DEAD_LETTER_QUEUE = "cooking-assistance.dlq";
  static final String GRANDMAS_ANSWER =
      """
      {"helpRequest":"%s","answerTitle":"Butter in a thin stream",\
      "answer":{"answerType":"PREPARATION_STEP_EXPLANATION","recipe":"%s","howToStep":"%s",\
      "description":"Add the butter in a thin stream"}}"""
          .formatted(
              HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.HOLLANDAISE.value(),
              HelpRequests.STEP_4.value());

  @Autowired HelpRequestRepository helpRequests;
  @Autowired JdbcClient jdbc;

  private void lenasRequestToGrandmaIsStored() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  /** The test plays the Grandma Avatar context: it owns the grandma-avatar exchange. */
  private void grandmaAnswers(String payload, String messageId) {
    admin.declareExchange(new TopicExchange("grandma-avatar", true, false));
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader("correlationId", "0b8f7a52-4bfa-4d4e-9d6d-1f4b9c8f9a10");
    properties.setHeader("messageId", messageId);
    properties.setHeader("source", "grandma-avatar");
    rabbit.send(
        "grandma-avatar",
        "grandma-avatar.help.provided",
        new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
  }

  private int messageCount(String queue) {
    Properties properties = admin.getQueueProperties(queue);
    return properties == null ? -1 : (Integer) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
  }

  @DisplayName("A5 DEAD_LETTER -> rejected without requeue -> in cooking-assistance.dlq with x-death reason, not redelivered -- B3-S02 US-CA-03 ADR0015")
  @Test
  void deadLetterOutcomeGoesToTheDeadLetterQueue() {
    lenasRequestToGrandmaIsStored();
    rabbit.execute(channel -> channel.queuePurge(DEAD_LETTER_QUEUE));
    String withoutTitle = GRANDMAS_ANSWER.replace("\"answerTitle\":\"Butter in a thin stream\",", "");

    grandmaAnswers(withoutTitle, "2c3d4e5f-6071-4829-9bac-1d2e3f4a5b6c");

    Eventually.within(
        Duration.ofSeconds(10), () -> assertThat(messageCount(DEAD_LETTER_QUEUE)).isEqualTo(1));
    Message deadLetter = rabbit.receive(DEAD_LETTER_QUEUE, 5_000);
    assertThat(deadLetter.getMessageProperties().getXDeathHeader())
        .singleElement()
        .satisfies(death -> assertThat(String.valueOf(death.get("reason"))).isEqualTo("rejected"));
    assertThat(messageCount(GRANDMA_QUEUE)).isZero();
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
  }

  @DisplayName("A6 RETRY -> redelivered; after 5 deliveries in cooking-assistance.dlq (quorum queue, delivery limit 5) -- B3-S02 US-CA-03 ADR0015")
  @Test
  void retryOutcomeIsDeadLetteredAfterFiveDeliveries() {
    lenasRequestToGrandmaIsStored();
    rabbit.execute(channel -> channel.queuePurge(DEAD_LETTER_QUEUE));
    doReturn(Outcome.RETRY).when(consumer).handle(any(), any());
    try {
      grandmaAnswers(GRANDMAS_ANSWER, "3d4e5f60-7182-493a-acbd-2e3f4a5b6c7d");

      Eventually.within(
          Duration.ofSeconds(20), () -> assertThat(messageCount(DEAD_LETTER_QUEUE)).isEqualTo(1));
      verify(consumer, times(5)).handle(any(), any());
    } finally {
      reset(consumer);
    }
  }

  @MockitoSpyBean GrandmaHelpProvidedConsumer consumer;

  @DisplayName("A7 the outbox poller publishes a pending row to the broker within 5 s -- B3-S01 US-CA-03 ADR0015 QS-08")
  @Test
  void pollerPublishesPendingOutboxRows() {
    String queue = probe(CREATED);

    lenasRequestToGrandmaIsStored();

    Message published = rabbit.receive(queue, 5_000);
    assertThat(published).isNotNull();
    assertThat(published.getMessageProperties().getHeaders())
        .containsEntry("correlationId", HelpRequests.HELP_REQUEST_ID.value().toString());
  }
}
