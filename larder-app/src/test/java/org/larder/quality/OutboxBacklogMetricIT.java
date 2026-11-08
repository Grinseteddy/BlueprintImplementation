package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceClock;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.acceptance.AcceptanceTime;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * QS-14: the outbox backlog is measurable on the Prometheus endpoint (ADR0020 addition). The relay
 * is switched off, so every row stays unpublished.
 */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "cooking-assistance.outbox.poller.enabled=false")
@Import({AcceptanceInfrastructure.class, AcceptanceTime.class, TestSigningKeys.class})
class OutboxBacklogMetricIT {

  @Value("${local.server.port}")
  int port;

  @Value("${local.management.port}")
  int managementPort;

  @Autowired AcceptanceClock clock;

  @DisplayName(
      "E4 unpublished outbox rows -> /actuator/prometheus shows the backlog count and the oldest row's age; above 100 rows or 1 min the alert thresholds are reached -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void backlogIsExposed() {
    Instant friday1800 = Instant.parse("2026-11-06T17:00:00Z");
    clock.moveTo(friday1800);
    lenaAsksTheCommunity();
    lenaAsksTheCommunity();

    clock.moveTo(friday1800.plus(Duration.ofMinutes(2)));
    String metrics =
        RestClient.create("http://localhost:" + managementPort)
            .get()
            .uri("/actuator/prometheus")
            .retrieve()
            .body(String.class);

    assertThat(gauge(metrics, "cooking_assistance_outbox_backlog")).as("backlog").isEqualTo(2.0);
    assertThat(gauge(metrics, "cooking_assistance_outbox_oldest_age_seconds"))
        .as("age of the oldest unpublished row, alert above 60 s")
        .isGreaterThanOrEqualTo(60.0);
  }

  private void lenaAsksTheCommunity() {
    RestClient.create("http://localhost:" + port)
        .post()
        .uri("/help-requests")
        .header(
            "Authorization",
            TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer())
        .header("version", "1.0.10")
        .contentType(MediaType.APPLICATION_JSON)
        .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY"))
        .retrieve()
        .toBodilessEntity();
  }

  /** The value of an unlabelled gauge line in the Prometheus text format. */
  private static double gauge(String metrics, String name) {
    Matcher line =
        Pattern.compile("(?m)^" + name + "(\\{[^}]*\\})? ([0-9.eE+-]+)$").matcher(metrics);
    if (!line.find()) {
      throw new AssertionError(name + " not exposed");
    }
    return Double.parseDouble(line.group(2));
  }
}
