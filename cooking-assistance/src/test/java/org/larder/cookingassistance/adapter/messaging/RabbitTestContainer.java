package org.larder.cookingassistance.adapter.messaging;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

/** RabbitMQ 3.13 for the slow suite (ADR0003, ADR0017). */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestContainer {

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbit() {
    return new RabbitMQContainer("rabbitmq:3.13-management-alpine");
  }
}
