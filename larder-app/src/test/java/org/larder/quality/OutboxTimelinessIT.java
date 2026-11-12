package org.larder.quality;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.acceptance.DatabaseReset;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * QS-08: an event reaches the broker within 5 s (p95) of its command's commit, on the real broker
 * and with the relay's production poll delay. Indicative: Testcontainers on one machine, not the
 * production network.
 */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class OutboxTimelinessIT {

  static final int REQUESTS = 20;

  @Value("${local.server.port}")
  int port;

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;
  @Autowired JdbcClient jdbc;

  @DisplayName(
      "E7 20 ingredient requests, one every 100 ms -> each HelpRequestCreated is on the broker within 5 s of its 201 (p95) -- B2-S01 US-CA-02 QS-08 op publishHelpRequestCreated")
  @Test
  void eventsReachTheBrokerWithinFiveSeconds() throws Exception {
    DatabaseReset.emptyAllTables(jdbc);
    ServiceChannels.listenToEvery(admin);
    // arrivals are taken while the requests are still being sent, not after the send loop
    Map<String, Instant> arrivedAt = new ConcurrentHashMap<>();
    ExecutorService receiver = Executors.newSingleThreadExecutor();
    Future<?> receiving = receiver.submit(() -> receiveAll(arrivedAt));
    Map<String, Instant> committedAt = new HashMap<>();
    for (int i = 0; i < REQUESTS; i++) {
      String id = lenaAsksForASubstitute();
      committedAt.put(id, Instant.now());
      Thread.sleep(100);
    }
    receiving.get(30, TimeUnit.SECONDS);
    receiver.shutdown();

    List<Duration> latencies = new ArrayList<>();
    committedAt.forEach(
        (id, committed) -> latencies.add(Duration.between(committed, arrivedAt.get(id))));
    latencies.sort(null);
    Duration p95 = latencies.get((int) Math.ceil(0.95 * REQUESTS) - 1);
    // the measured value is reported with the quality results (indicative environment)
    LoggerFactory.getLogger(OutboxTimelinessIT.class)
        .info("QS-08 commit to broker: p95 {}, max {}", p95, latencies.getLast());
    assertThat(p95)
        .as("p95 from commit to broker, all: %s", latencies)
        .isLessThanOrEqualTo(Duration.ofSeconds(5));
  }

  /** Every HelpRequestCreated, with the moment it is taken from the test's queue. */
  private Void receiveAll(Map<String, Instant> arrivedAt) throws Exception {
    while (arrivedAt.size() < REQUESTS) {
      Message message = rabbit.receive(ServiceChannels.queueOn(ServiceChannels.CREATED), 10_000);
      assertThat(message)
          .as("HelpRequestCreated %d of %d", arrivedAt.size() + 1, REQUESTS)
          .isNotNull();
      Instant arrived = Instant.now();
      arrivedAt.put(
          new ObjectMapper().readTree(message.getBody()).get("helpRequestId").asText(), arrived);
    }
    return null;
  }

  /** Lena asks the Community for a substitute; the id of the created request. */
  private String lenaAsksForASubstitute() throws Exception {
    String created =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/help-requests")
            .header(
                "Authorization",
                TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer())
            .header("version", "1.0.15")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"title":"Crème fraîche in tomato soup","type":"INGREDIENT_SUBSTITUTE",
                 "description":"The shop has none","recipe":"%s","ingredient":"%s",
                 "ingredientName":"crème fraîche","substituteRequirements":"200 g, not available",
                 "preferredProvider":["COMMUNITY"]}"""
                    .formatted(
                        HelpRequests.TOMATO_SOUP.value(), HelpRequests.CREME_FRAICHE.value()))
            .retrieve()
            .body(String.class);
    return new ObjectMapper().readTree(created.getBytes(UTF_8)).get("helpRequestId").asText();
  }
}
