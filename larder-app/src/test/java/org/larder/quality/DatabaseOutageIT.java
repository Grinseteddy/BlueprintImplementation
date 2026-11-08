package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * QS-05 (CI job, quality.yml): without the database the API answers 503 and recovers by itself. The
 * database is paused on test containers (indicative).
 */
@Tag("fault-injection")
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class DatabaseOutageIT {

  @Value("${local.server.port}")
  int port;

  @Autowired PostgreSQLContainer postgres;
  @Autowired JdbcClient jdbc;

  @DisplayName(
      "E6 database unavailable -> commands and queries answered 503 Problem, no partial writes; recovers without a restart -- B3-S01 US-CA-03 QS-05")
  @Test
  void answers503AndRecovers() {
    String existing = idOf(lenaAsks());
    int storedBefore = storedRequests();

    postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
    ResponseEntity<String> command;
    ResponseEntity<String> query;
    try {
      command = lenaAsks();
      query = read(existing);
    } finally {
      postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
    }
    ResponseEntity<String> afterwards = lenaAsks();

    assertThat(command.getStatusCode().value()).as("command during the outage").isEqualTo(503);
    assertThat(command.getBody()).contains("\"code\":\"SERVICE_UNAVAILABLE\"");
    assertThat(query.getStatusCode().value()).as("query during the outage").isEqualTo(503);
    assertThat(afterwards.getStatusCode().value()).as("command after the outage").isEqualTo(201);
    assertThat(storedRequests())
        .as(
            "requests stored: one more than before the outage (the one after), nothing half-written")
        .isEqualTo(storedBefore + 1);
  }

  private ResponseEntity<String> lenaAsks() {
    return client()
        .post()
        .uri("/help-requests")
        .header("Authorization", lenasToken())
        .header("version", "1.0.10")
        .contentType(MediaType.APPLICATION_JSON)
        .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY"))
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  private ResponseEntity<String> read(String helpRequestId) {
    return client()
        .get()
        .uri("/help-requests/" + helpRequestId)
        .header("Authorization", lenasToken())
        .header("version", "1.0.10")
        .retrieve()
        .onStatus(HttpStatusCode::isError, (request, response) -> {})
        .toEntity(String.class);
  }

  /** Help requests in the store; other tests of the shared context may have stored some too. */
  private int storedRequests() {
    return jdbc.sql("select count(*) from cooking_assistance.help_request")
        .query(Integer.class)
        .single();
  }

  private static String idOf(ResponseEntity<String> created) {
    return created.getBody().replaceAll(".*\"helpRequestId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  /** Gives up after 60 s, so a request hanging on the database fails the test, not the build. */
  private RestClient client() {
    JdkClientHttpRequestFactory requests =
        new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    requests.setReadTimeout(Duration.ofSeconds(60));
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .requestFactory(requests)
        .build();
  }

  private static String lenasToken() {
    return TestTokens.forCook(HelpRequests.LENA)
        .roles("cook")
        .scope("help:read help:write")
        .bearer();
  }
}
