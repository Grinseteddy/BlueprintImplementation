package org.larder.cookingassistance.adapter.out.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** The broker behind the publisher port: RabbitMQ with publisher confirms (ADR0003, ADR0015). */
@Component
class AmqpMessagePublisher implements MessagePublisher {

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

  private final RabbitTemplate rabbit;

  AmqpMessagePublisher(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  /** ADR0015: returns only when the broker confirmed the message; throws otherwise. */
  @Override
  public void publish(OutgoingMessage message) {
    CorrelationData correlation = new CorrelationData(message.messageId().toString());
    rabbit.send(AmqpPublishing.EXCHANGE, message.channel(), toAmqp(message), correlation);
    Confirm confirm = awaitConfirm(correlation);
    if (!confirm.isAck()) {
      throw new AmqpException(
          "broker refused message " + message.messageId() + ": " + confirm.getReason());
    }
    // the broker returns an unroutable mandatory message before it confirms it
    if (correlation.getReturned() != null) {
      throw new AmqpException("no queue is bound for " + message.channel());
    }
  }

  private static Confirm awaitConfirm(CorrelationData correlation) {
    try {
      return correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AmqpException("interrupted while waiting for the broker's confirm", e);
    } catch (ExecutionException | TimeoutException e) {
      throw new AmqpException("no confirm from the broker", e);
    }
  }

  /** AsyncAPI operation binding persistentPublish: delivery mode 2. */
  private static Message toAmqp(OutgoingMessage message) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(message.messageId().toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    message.headers().forEach(properties::setHeader);
    return new Message(message.payload().getBytes(StandardCharsets.UTF_8), properties);
  }
}
