package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** The host's broker connection comes from the environment only (ADR0012, ADR0019). */
class BrokerSettingsTest {

  private static RabbitProperties brokerSettingsWith(Map<String, Object> environment)
      throws Exception {
    StandardEnvironment env = new StandardEnvironment();
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    env.getPropertySources().addFirst(new MapPropertySource("environment", environment));
    List<PropertySource<?>> yaml =
        new YamlPropertySourceLoader()
            .load("application.yml", new ClassPathResource("application.yml"));
    yaml.forEach(env.getPropertySources()::addLast);
    return Binder.get(env).bindOrCreate("spring.rabbitmq", RabbitProperties.class);
  }

  @DisplayName(
      "no LARDER_BROKER_* variables -> the host does not start, naming LARDER_BROKER_HOST; never localhost as guest -- B3-S02 US-CA-03 OWASP-API8 ADR0012")
  @Test
  void hostWithoutBrokerEnvironmentDoesNotStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(BrokerEnvironmentCheck.class)
        .run(context -> assertThat(context).hasFailed());
    new ApplicationContextRunner()
        .withUserConfiguration(BrokerEnvironmentCheck.class)
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("LARDER_BROKER_HOST"));
  }

  @DisplayName(
      "LARDER_BROKER_HOST, _USERNAME, _PASSWORD set -> the host starts -- B3-S02 US-CA-03 OWASP-API8 ADR0012")
  @Test
  void hostWithBrokerEnvironmentStarts() {
    new ApplicationContextRunner()
        .withUserConfiguration(BrokerEnvironmentCheck.class)
        .withPropertyValues(
            "LARDER_BROKER_HOST=broker.larder.example",
            "LARDER_BROKER_USERNAME=cooking-assistance",
            "LARDER_BROKER_PASSWORD=from-the-vault")
        .run(context -> assertThat(context).hasNotFailed());
  }

  @DisplayName(
      "LARDER_BROKER_* set -> host, user and password from them, TLS on by default (amqps) -- B3-S02 US-CA-03 OWASP-API8 ADR0012")
  @Test
  void brokerSettingsComeFromTheEnvironment() throws Exception {
    RabbitProperties broker =
        brokerSettingsWith(
            Map.of(
                "LARDER_BROKER_HOST", "broker.larder.example",
                "LARDER_BROKER_USERNAME", "cooking-assistance",
                "LARDER_BROKER_PASSWORD", "from-the-vault"));

    assertThat(broker.getHost()).isEqualTo("broker.larder.example");
    assertThat(broker.getUsername()).isEqualTo("cooking-assistance");
    assertThat(broker.getPassword()).isEqualTo("from-the-vault");
    assertThat(broker.getSsl().getEnabled()).isTrue();
  }
}
