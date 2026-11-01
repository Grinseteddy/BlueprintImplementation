package org.larder.cookingassistance.adapter.out.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/** The exchange this context publishes to, as its AsyncAPI channel binding declares it. */
@Configuration
@PropertySource("classpath:cooking-assistance-messaging.properties")
class AmqpPublishing {

  // AsyncAPI channelBindings.cookingAssistanceExchange: topic, durable, not auto-deleted
  static final String EXCHANGE = "cooking-assistance";

  @Bean
  TopicExchange cookingAssistanceExchange() {
    return new TopicExchange(EXCHANGE, true, false);
  }
}
