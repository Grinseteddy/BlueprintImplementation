package org.larder.cookingassistance.adapter.messaging;

import org.larder.cookingassistance.fixtures.TestBroker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** RabbitMQ 3.13 for the slow suite (ADR0003, ADR0017). */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestContainer {

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbit() {
    return TestBroker.withGrandmaAvatarsExchange();
  }
}
