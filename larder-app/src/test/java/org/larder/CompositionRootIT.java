package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The Larder process with production configuration on PostgreSQL and RabbitMQ. */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

  /** A request written byte for byte, past any client's checks; the whole raw response. */
  private String exchangeRaw(String request) throws IOException {
    try (Socket socket = new Socket("localhost", port)) {
      socket.getOutputStream().write(request.getBytes(StandardCharsets.ISO_8859_1));
      return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @DisplayName(
      "B1 the service starts with production configuration; Actuator health and readiness UP -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void serviceStartsAndReportsHealthAndReadiness() {
    for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
      ResponseEntity<String> response = probe(probe);
      assertThat(response.getStatusCode().value()).as(probe).isEqualTo(200);
      assertThat(response.getBody()).as(probe).contains("\"status\":\"UP\"");
    }
  }

  @DisplayName(
      "B2 a header line with a control character (RFC 9112), refused by Tomcat before Spring -> 400 application/problem+json VALIDATION_FAILED, not Tomcat's HTML page -- B3-S01 US-CA-03 edge (step 12, Schemathesis)")
  @Test
  void malformedHeaderLineIsAProblem() throws Exception {
    String response =
        exchangeRaw(
            "GET /help-requests HTTP/1.1\r\nHost: localhost\r\nX-Probe: a\u0000b\r\nConnection: close\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 400");
    assertThat(response).containsIgnoringCase("Content-Type: application/problem+json");
    assertThat(response).contains("\"code\":\"VALIDATION_FAILED\"");
  }

  @DisplayName(
      "B3 TRACE /help-requests with a valid token, refused by Tomcat -> 405 application/problem+json METHOD_NOT_ALLOWED, not 401 from the error dispatch -- B3-S01 US-CA-03 edge (step 12, Schemathesis)")
  @Test
  void traceIsMethodNotAllowed() throws Exception {
    String token =
        TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read help:write").bearer();
    String response =
        exchangeRaw(
            "TRACE /help-requests HTTP/1.1\r\nHost: localhost\r\nAuthorization: "
                + token
                + "\r\nversion: 1.0.9\r\nConnection: close\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 405");
    assertThat(response).containsIgnoringCase("Content-Type: application/problem+json");
    assertThat(response).contains("\"code\":\"METHOD_NOT_ALLOWED\"");
  }
}
