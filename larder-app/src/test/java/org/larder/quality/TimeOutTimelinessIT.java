package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;
import static org.larder.quality.ServiceChannels.TIMED_OUT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

  @DisplayName(
      "E3 once the clock passes Grandma's deadline of a request to both, her side times out within 30 s, the Community's side still asked, no HelpRequestTimedOut -- B3-S06 US-CA-04 INV-26 QS-09")
  @Test
  void oneSideTimesOutWithin30Seconds() throws Exception {
    ServiceChannels.listenToEvery(admin);
    String queue = ServiceChannels.queueOn(TIMED_OUT);
    clock.moveTo(SATURDAY_18_40);
    String helpRequestId = lenaAsksBothAboutHerBurntChicken();

    clock.moveTo(SATURDAY_18_40.plus(Duration.ofMinutes(5)).plusSeconds(1));
    long passed = System.nanoTime();
    JsonNode request = sideTimedOutWithin(helpRequestId, "GRANDMA_AVATAR", Duration.ofSeconds(40));
    Duration timeliness = Duration.ofNanos(System.nanoTime() - passed);

    assertThat(timeliness)
        .as("time from Grandma's deadline to her side timed out")
        .isLessThanOrEqualTo(Duration.ofSeconds(30));
    assertThat(stateOf(request, "COMMUNITY")).as("the Community's side").isEqualTo("ASKED");
    assertThat(request.get("status").asText()).isEqualTo("OPEN");
    assertThat(timedOutWithin(queue, helpRequestId, Duration.ofSeconds(12)))
        .as("a HelpRequestTimedOut while the Community's side is still asked")
        .isNull();
  }

  @DisplayName(
      "E4 two service instances, a request to both: Grandma's side times out within 30 s of her deadline with no event, then exactly one HelpRequestTimedOut within 30 s of the Community's deadline (indicative) -- B3-S06 US-CA-04 INV-26 QS-09 ADR0021")
  @Test
  void twoInstancesTimeOutEachSideOnce() throws Exception {
    ServiceChannels.listenToEvery(admin);
    String queue = ServiceChannels.queueOn(TIMED_OUT);
    try (ConfigurableApplicationContext second = secondInstance()) {
      AcceptanceClock secondClock = second.getBean(AcceptanceClock.class);
      clock.moveTo(SATURDAY_18_40);
      secondClock.moveTo(SATURDAY_18_40);
      String helpRequestId = lenaAsksBothAboutHerBurntChicken();

      Instant afterGrandmasDeadline = SATURDAY_18_40.plus(Duration.ofMinutes(5)).plusSeconds(1);
      clock.moveTo(afterGrandmasDeadline);
      secondClock.moveTo(afterGrandmasDeadline);
      long grandmasPassed = System.nanoTime();
      sideTimedOutWithin(helpRequestId, "GRANDMA_AVATAR", Duration.ofSeconds(40));
      assertThat(Duration.ofNanos(System.nanoTime() - grandmasPassed))
          .as("time from Grandma's deadline to her side timed out")
          .isLessThanOrEqualTo(Duration.ofSeconds(30));
      assertThat(timedOutWithin(queue, helpRequestId, Duration.ofSeconds(12)))
          .as("a HelpRequestTimedOut after Grandma's deadline only")
          .isNull();

      Instant afterTheCommunitysDeadline =
          SATURDAY_18_40.plus(Duration.ofMinutes(15)).plusSeconds(1);
      clock.moveTo(afterTheCommunitysDeadline);
      secondClock.moveTo(afterTheCommunitysDeadline);
      long communitysPassed = System.nanoTime();
      JsonNode timedOut = timedOutWithin(queue, helpRequestId, Duration.ofSeconds(40));
      assertThat(timedOut).as("HelpRequestTimedOut after the Community's deadline").isNotNull();
      assertThat(Duration.ofNanos(System.nanoTime() - communitysPassed))
          .as("time from the Community's deadline to HelpRequestTimedOut")
          .isLessThanOrEqualTo(Duration.ofSeconds(30));
      List<String> timedOutProviders = new ArrayList<>();
      timedOut
          .get("timedOutProviders")
          .forEach(provider -> timedOutProviders.add(provider.asText()));
      assertThat(timedOutProviders).containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
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
            "--management.server.port=0",
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
            .header("version", "1.0.10")
            .contentType(MediaType.APPLICATION_JSON)
            .body(HelpRequestBodies.sauceSplitsAtStep4("COMMUNITY"))
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(created).get("helpRequestId").asText();
  }

  /** Saturday 18:40 CET (US-CA-04 R1.E1). */
  private static final Instant SATURDAY_18_40 = Instant.parse("2026-11-14T17:40:00Z");

  private String lenaAsksBothAboutHerBurntChicken() throws Exception {
    String created =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/help-requests")
            .header(
                "Authorization",
                TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer())
            .header("version", "1.0.18")
            .contentType(MediaType.APPLICATION_JSON)
            .body(HelpRequestBodies.burntChicken("COMMUNITY", "GRANDMA_AVATAR"))
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(created).get("helpRequestId").asText();
  }

  /**
   * Jonas reads the request every 500 ms until the provider's side timed out (no event of its own).
   */
  private JsonNode sideTimedOutWithin(String helpRequestId, String provider, Duration within)
      throws Exception {
    long deadline = System.nanoTime() + within.toNanos();
    while (System.nanoTime() < deadline) {
      JsonNode request = jonasReads(helpRequestId);
      if (stateOf(request, provider).equals("TIMED_OUT")) {
        return request;
      }
      Thread.sleep(500);
    }
    throw new AssertionError(
        provider + "'s side of " + helpRequestId + " not TIMED_OUT within " + within);
  }

  private JsonNode jonasReads(String helpRequestId) throws Exception {
    String body =
        RestClient.create("http://localhost:" + port)
            .get()
            .uri("/help-requests/{id}", helpRequestId)
            .header(
                "Authorization",
                TestTokens.forCook(HelpRequests.JONAS)
                    .roles("cook", "community")
                    .scope("help:read")
                    .bearer())
            .header("version", "1.0.18")
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(body);
  }

  private static String stateOf(JsonNode request, String provider) {
    for (JsonNode asked : request.get("askedProviders")) {
      if (asked.get("providerType").asText().equals(provider)) {
        return asked.get("state").asText();
      }
    }
    throw new AssertionError("no " + provider + " among " + request.get("askedProviders"));
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
