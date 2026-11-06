package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The host's database connection comes from the environment only (ADR0009, ADR0019). */
class DatabaseSettingsTest {

  private static DataSourceProperties databaseSettingsWith(Map<String, Object> environment)
      throws Exception {
    return Binder.get(HostSettings.resolvedWith(environment))
        .bindOrCreate("spring.datasource", DataSourceProperties.class);
  }

  @DisplayName(
      "no LARDER_DB_* variables -> the host does not start, naming LARDER_DB_URL -- B3-S01 US-CA-03 OWASP-API8 ADR0009")
  @Test
  void hostWithoutDatabaseEnvironmentDoesNotStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(DatabaseEnvironmentCheck.class)
        .run(
            context ->
                assertThat(context.getStartupFailure()).hasStackTraceContaining("LARDER_DB_URL"));
  }

  @DisplayName(
      "LARDER_DB_URL, _USERNAME, _PASSWORD set -> the database URL, user and password come from them -- B3-S01 US-CA-03 OWASP-API8 ADR0009")
  @Test
  void databaseSettingsComeFromTheEnvironment() throws Exception {
    DataSourceProperties database =
        databaseSettingsWith(
            Map.of(
                "LARDER_DB_URL", "jdbc:postgresql://db.larder.example/larder",
                "LARDER_DB_USERNAME", "cooking_assistance",
                "LARDER_DB_PASSWORD", "from-the-vault"));

    assertThat(database.getUrl()).isEqualTo("jdbc:postgresql://db.larder.example/larder");
    assertThat(database.getUsername()).isEqualTo("cooking_assistance");
    assertThat(database.getPassword()).isEqualTo("from-the-vault");
  }

  @DisplayName(
      "LARDER_DB_PASSWORD blank, URL and user set -> the host does not start, naming LARDER_DB_PASSWORD -- B3-S01 US-CA-03 OWASP-API8 ADR0009")
  @Test
  void hostWithBlankDatabasePasswordDoesNotStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(DatabaseEnvironmentCheck.class)
        .withPropertyValues(
            "LARDER_DB_URL=jdbc:postgresql://db.larder.example/larder",
            "LARDER_DB_USERNAME=cooking_assistance",
            "LARDER_DB_PASSWORD= ")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("LARDER_DB_PASSWORD"));
  }

  /** The PostgreSQL driver's socket timeout as application.yml resolves it (seconds). */
  private static String socketTimeoutWith(Map<String, Object> environment) throws Exception {
    return HostSettings.resolvedWith(environment)
        .getProperty("spring.datasource.hikari.data-source-properties.socketTimeout");
  }

  @DisplayName(
      "H3 LARDER_DB_SOCKET_TIMEOUT=7 -> the PostgreSQL driver's socketTimeout is 7 s; unset -> 10 s, so a database that stops answering never holds a request without limit -- B3-S01 US-CA-03 QS-05 ADR0019 (step 12, DatabaseOutageIT)")
  @Test
  void socketTimeoutFollowsTheEnvironment() throws Exception {
    assertThat(socketTimeoutWith(Map.of("LARDER_DB_SOCKET_TIMEOUT", "7")))
        .as("LARDER_DB_SOCKET_TIMEOUT=7")
        .isEqualTo("7");
    assertThat(socketTimeoutWith(Map.of())).as("unset").isEqualTo("10");
  }
}
