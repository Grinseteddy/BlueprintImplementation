package org.larder.cookingassistance.adapter.out.persistence;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 16 container per test run (ADR0017): Spring's context cache reuses it for every
 * test class that imports this configuration.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:16-alpine");
  }
}
