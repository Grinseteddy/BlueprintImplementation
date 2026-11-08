package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;

/** Metrics answer on the management port only (ADR0020, SEC-16). */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class ManagementPortIT {

  @Value("${local.server.port}")
  int apiPort;

  @Autowired Environment environment;

  @DisplayName(
      "M2 /actuator/prometheus without a token -> 404 on the API port, 200 on the management port -- B3-S01 US-CA-03 OWASP-API8 ADR0020 SEC-16")
  @Test
  void metricsAnswerOnTheManagementPortOnly() {
    assertThat(statusOfPrometheusOn(apiPort)).as("API port").isEqualTo(404);
    assertThat(
            statusOfPrometheusOn(
                environment.getRequiredProperty("local.management.port", Integer.class)))
        .as("management port")
        .isEqualTo(200);
  }

  private static int statusOfPrometheusOn(int port) {
    return RestClient.create("http://localhost:" + port)
        .get()
        .uri("/actuator/prometheus")
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toBodilessEntity()
        .getStatusCode()
        .value();
  }
}
