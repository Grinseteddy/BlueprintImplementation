package org.larder.cookingassistance.adapter.in.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The queues this context owns and consumes from, as its AsyncAPI channels declare them. */
@Configuration
class AmqpConsuming {

  // AsyncAPI channel grandmaHelpProvidedQueue
  static final String GRANDMA_QUEUE = "cooking-assistance.grandma-help-provided";
  // ADR0015: dead letters after 5 deliveries
  static final String DEAD_LETTER_QUEUE = "cooking-assistance.dlq";
  // RabbitMQ counts the redeliveries allowed after the first delivery: 4 means 5 deliveries
  static final int DELIVERY_LIMIT = 4;

  @Bean
  Queue grandmaHelpProvidedQueue() {
    return QueueBuilder.durable(GRANDMA_QUEUE)
        .quorum()
        .deliveryLimit(DELIVERY_LIMIT)
        .deadLetterExchange("")
        .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  Queue deadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  /**
   * The Grandma Avatar context owns this exchange; it is declared here too (same arguments, so the
   * declaration is idempotent) so the binding can be made whichever context starts first.
   */
  @Bean
  TopicExchange grandmaAvatarExchange() {
    return new TopicExchange("grandma-avatar", true, false);
  }

  @Bean
  Binding grandmaHelpProvidedBinding() {
    return BindingBuilder.bind(grandmaHelpProvidedQueue())
        .to(grandmaAvatarExchange())
        .with("grandma-avatar.help.provided");
  }
}
