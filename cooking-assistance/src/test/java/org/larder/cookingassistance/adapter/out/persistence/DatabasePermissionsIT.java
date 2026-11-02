package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * The module runs with its own database user that owns only its schema (ADR0009): it migrates and
 * stores there, and nowhere else.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
class DatabasePermissionsIT {

  static final String USER = "cooking_assistance";
  static final String PASSWORD = "test-only";
  static final PostgreSQLContainer<?> POSTGRES = databaseWithTheModulesUser();

  @DynamicPropertySource
  static void connectAsTheModulesUser(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> USER);
    registry.add("spring.datasource.password", () -> PASSWORD);
  }

  /** The database administrator creates the user and its schema; the module owns nothing else. */
  private static PostgreSQLContainer<?> databaseWithTheModulesUser() {
    PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
    try {
      psql(
          postgres,
          "CREATE ROLE " + USER + " LOGIN PASSWORD '" + PASSWORD + "'",
          "CREATE SCHEMA cooking_assistance AUTHORIZATION " + USER,
          "REVOKE ALL ON SCHEMA public FROM PUBLIC");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return postgres;
  }

  private static void psql(PostgreSQLContainer<?> postgres, String... statements) throws Exception {
    for (String statement : statements) {
      var result =
          postgres.execInContainer(
              "psql",
              "-U",
              postgres.getUsername(),
              "-d",
              postgres.getDatabaseName(),
              "-v",
              "ON_ERROR_STOP=1",
              "-c",
              statement);
      if (result.getExitCode() != 0) {
        throw new IllegalStateException(statement + ": " + result.getStderr());
      }
    }
  }

  @Autowired HelpRequestRepository repository;
  @Autowired JdbcClient jdbc;

  @DisplayName(
      "the module's own user, owner of schema cooking_assistance only -> migrations run, Lena's request is stored and read back; a table in public is refused -- B3-S01 US-CA-03 OWASP-API8 ADR0009")
  @Test
  void theModulesOwnUserWorksInItsSchemaOnly() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID)).isPresent();
    assertThatThrownBy(() -> jdbc.sql("CREATE TABLE public.elsewhere (id int)").update())
        .rootCause()
        .hasMessageContaining("permission denied for schema public");
  }
}
