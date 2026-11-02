package org.larder.cookingassistance.adapter.in.messaging;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** The AMQP binding of the consumer: raw message in, outcome as the broker action (ADR0015). */
@Component
class GrandmaHelpProvidedListener {

  private final GrandmaHelpProvidedConsumer consumer;

  GrandmaHelpProvidedListener(GrandmaHelpProvidedConsumer consumer) {
    this.consumer = consumer;
  }

  // op: consumeGrandmaHelpProvided (channel cooking-assistance.grandma-help-provided)
  @RabbitListener(queues = AmqpConsuming.GRANDMA_QUEUE, ackMode = "MANUAL")
  void onMessage(Message message, Channel channel) throws IOException {
    Outcome outcome = consumer.handle(headersOf(message), message.getBody());
    long deliveryTag = message.getMessageProperties().getDeliveryTag();
    switch (outcome) {
      case ACK -> channel.basicAck(deliveryTag, false);
      // the queue's dead-letter exchange routes it to cooking-assistance.dlq
      case DEAD_LETTER -> channel.basicReject(deliveryTag, false);
      // back to the queue; the queue's delivery limit dead-letters it in the end
      case RETRY -> channel.basicNack(deliveryTag, false, true);
    }
  }

  /** AMQP header values arrive as the client's string types; the consumer reads plain strings. */
  private static Map<String, Object> headersOf(Message message) {
    Map<String, Object> headers = new HashMap<>();
    message
        .getMessageProperties()
        .getHeaders()
        .forEach((name, value) -> headers.put(name, String.valueOf(value)));
    return headers;
  }
}
