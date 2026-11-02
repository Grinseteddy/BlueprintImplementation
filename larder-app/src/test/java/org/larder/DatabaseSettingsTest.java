package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** The host's database connection comes from the environment only (ADR0009, ADR0019). */
class DatabaseSettingsTest {

  private static DataSourceProperties databaseSettingsWith(Map<String, Object> environment)
      throws Exception {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    env.getPropertySources().addFirst(new MapPropertySource("environment", environment));
    List<PropertySource<?>> yaml =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    yaml.forEach(env.getPropertySources()::addLast);
    return Binder.get(env).bindOrCreate("spring.datasource", DataSourceProperties.class);
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
}
