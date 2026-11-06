package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.larder.quality.ServiceChannels.TIMED_OUT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceClock;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.acceptance.AcceptanceTime;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * QS-09: a deadline passed without an answer times the request out in time, exactly once. Runs with
 * the production poll delay (10 s), on real PostgreSQL and RabbitMQ.
 */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "LARDER_TIMEOUT_POLL_DELAY=10s")
@Import({AcceptanceInfrastructure.class, AcceptanceTime.class, TestSigningKeys.class})
class TimeOutTimelinessIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;
  @Autowired AcceptanceClock clock;
  @Autowired PostgreSQLContainer postgres;
  @Autowired RabbitMQContainer broker;

  @DisplayName(
      "E1 once the clock passes the deadline, the request times out within 30 s, with exactly one HelpRequestTimedOut -- B3-S06 US-CA-03 QS-09")
  @Test
  void timesOutWithin30Seconds() throws Exception {
    ServiceChannels.listenToEvery(admin);
    String queue = ServiceChannels.queueOn(TIMED_OUT);
    Instant friday1800 = Instant.parse("2026-11-06T17:00:00Z");
    clock.moveTo(friday1800);
    String helpRequestId = lenaAsksTheCommunity();

    clock.moveTo(friday1800.plus(Duration.ofHours(12)).plusSeconds(1));
    long passed = System.nanoTime();
    timedOutFor(queue, helpRequestId, Duration.ofSeconds(40));
    Duration timeliness = Duration.ofNanos(System.nanoTime() - passed);

    assertThat(timeliness)
        .as("time from the deadline to HelpRequestTimedOut")
        .isLessThanOrEqualTo(Duration.ofSeconds(30));
    assertThat(timedOutWithin(queue, helpRequestId, Duration.ofSeconds(12)))
        .as("a second HelpRequestTimedOut for the same request")
        .isNull();
  }

  @DisplayName(
      "E2 two service instances on one database and broker -> exactly one HelpRequestTimedOut (indicative) -- B3-S06 US-CA-03 QS-09 ADR0021")
  @Test
  void twoInstancesTimeOutOnce() throws Exception {
    ServiceChannels.listenToEvery(admin);
    String queue = ServiceChannels.queueOn(TIMED_OUT);
    try (ConfigurableApplicationContext second = secondInstance()) {
      AcceptanceClock secondClock = second.getBean(AcceptanceClock.class);
      Instant friday1800 = Instant.parse("2026-11-06T17:00:00Z");
      clock.moveTo(friday1800);
      secondClock.moveTo(friday1800);
      String helpRequestId = lenaAsksTheCommunity();

      Instant afterTheDeadline = friday1800.plus(Duration.ofHours(12)).plusSeconds(1);
      clock.moveTo(afterTheDeadline);
      secondClock.moveTo(afterTheDeadline);
      timedOutFor(queue, helpRequestId, Duration.ofSeconds(40));

      assertThat(timedOutWithin(queue, helpRequestId, Duration.ofSeconds(12)))
          .as("a second HelpRequestTimedOut from the other instance")
          .isNull();
    }
  }

  /** A second Larder process on the same database and broker, with its own clock (indicative). */
  private ConfigurableApplicationContext secondInstance() {
    return new SpringApplicationBuilder(LarderApplication.class, AcceptanceTime.class)
        .run(
            "--server.port=0",
            "--LARDER_DB_URL=" + postgres.getJdbcUrl(),
            "--LARDER_DB_USERNAME=" + postgres.getUsername(),
            "--LARDER_DB_PASSWORD=" + postgres.getPassword(),
            "--LARDER_BROKER_HOST=" + broker.getHost(),
            "--LARDER_BROKER_PORT=" + broker.getAmqpPort(),
            "--LARDER_BROKER_USERNAME=" + broker.getAdminUsername(),
            "--LARDER_BROKER_PASSWORD=" + broker.getAdminPassword(),
            "--LARDER_BROKER_SSL=false",
            "--LARDER_TIMEOUT_POLL_DELAY=10s");
  }

  private String lenaAsksTheCommunity() throws Exception {
    String created =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/help-requests")
            .header(
                "Authorization",
                TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer())
            .header("version", "1.0.9")
            .contentType(MediaType.APPLICATION_JSON)
            .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY"))
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(created).get("helpRequestId").asText();
  }

  private void timedOutFor(String queue, String helpRequestId, Duration within) throws Exception {
    if (timedOutWithin(queue, helpRequestId, within) == null) {
      throw new AssertionError("no HelpRequestTimedOut for " + helpRequestId + " within " + within);
    }
  }

  /** The HelpRequestTimedOut of this request within the time, or null. */
  private JsonNode timedOutWithin(String queue, String helpRequestId, Duration within)
      throws Exception {
    long deadline = System.nanoTime() + within.toNanos();
    while (System.nanoTime() < deadline) {
      Message message = rabbit.receive(queue, 500);
      if (message != null) {
        JsonNode payload = new ObjectMapper().readTree(message.getBody());
        if (payload.get("helpRequest").asText().equals(helpRequestId)) {
          return payload;
        }
      }
    }
    return null;
  }
}
