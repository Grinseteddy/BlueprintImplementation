package org.larder.cookingassistance.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The queues this context owns and consumes from, as its AsyncAPI channels declare them. */
@Configuration
class AmqpConsuming {

  // AsyncAPI channel grandmaHelpProvidedQueue
  static final String GRANDMA_QUEUE = "cooking-assistance.grandma-help-provided";
  // ADR0015: dead letters after 5 deliveries
  static final String DEAD_LETTER_QUEUE = "cooking-assistance.dlq";
  // the module's own dead-letter exchange: the default exchange would need write rights on
  // amq.default, which would let the module publish to any queue (ADR0012 addition)
  static final String DEAD_LETTER_EXCHANGE = "cooking-assistance.dlx";
  // RabbitMQ counts the redeliveries allowed after the first delivery: 4 means 5 deliveries
  static final int DELIVERY_LIMIT = 4;

  @Bean
  Queue grandmaHelpProvidedQueue() {
    return QueueBuilder.durable(GRANDMA_QUEUE)
        .quorum()
        .deliveryLimit(DELIVERY_LIMIT)
        .deadLetterExchange(DEAD_LETTER_EXCHANGE)
        .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  DirectExchange deadLetterExchange() {
    return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
  }

  @Bean
  Binding deadLetterBinding() {
    return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with(DEAD_LETTER_QUEUE);
  }

  /**
   * The Grandma Avatar context owns and declares its exchange; the module only binds to it by name,
   * and the binding fails while the exchange is missing (ADR0012 addition, SEC-04).
   */
  @Bean
  Binding grandmaHelpProvidedBinding() {
    return new Binding(
        GRANDMA_QUEUE,
        Binding.DestinationType.QUEUE,
        "grandma-avatar",
        "grandma-avatar.help.provided",
        null);
  }
}
