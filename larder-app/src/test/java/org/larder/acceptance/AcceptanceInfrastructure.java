package org.larder.acceptance;

import org.larder.cookingassistance.fixtures.TestBroker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/** The real infrastructure the running service uses: PostgreSQL 16 and RabbitMQ 3.13. */
@TestConfiguration(proxyBeanMethods = false)
public class AcceptanceInfrastructure {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>("postgres:16-alpine");
  }

  @Bean
  @ServiceConnection
  RabbitMQContainer rabbit() {
    return TestBroker.withGrandmaAvatarsExchange();
  }
}
