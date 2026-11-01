package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The Larder process with production configuration on PostgreSQL and RabbitMQ. */
@SpringBootTest(classes = LarderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class CompositionRootIT {

  @Value("${local.server.port}")
  int port;

  private ResponseEntity<String> probe(String path) {
    return RestClient.create("http://localhost:" + port)
        .get()
        .uri(path)
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  @DisplayName("B1 the service starts with production configuration; Actuator health and readiness UP -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void serviceStartsAndReportsHealthAndReadiness() {
    for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
      ResponseEntity<String> response = probe(probe);
      assertThat(response.getStatusCode().value()).as(probe).isEqualTo(200);
      assertThat(response.getBody()).as(probe).contains("\"status\":\"UP\"");
    }
  }
}
