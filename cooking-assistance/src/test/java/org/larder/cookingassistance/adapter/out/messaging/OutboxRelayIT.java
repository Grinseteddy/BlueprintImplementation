package org.larder.cookingassistance.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** The outbox relay against PostgreSQL with a fake broker behind the publisher port (ADR0015). */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class OutboxRelayIT {

  static final UUID A = HelpRequests.HELP_REQUEST_ID.value();
  static final UUID B = UUID.fromString("0f1e2d3c-4b5a-4968-8776-655443322110");

  @Autowired JdbcClient jdbc;
  @Autowired TransactionTemplate transactions;

  final RecordingPublisher publisher = new RecordingPublisher();

  /** Friday 12:00:00 UTC; the relay's clock in every run unless a test says otherwise. */
  static final Instant NOON = Instant.parse("2026-11-06T12:00:00Z");

  OutboxRelay relay() {
    return relayAt(NOON);
  }

  OutboxRelay relayAt(Instant now) {
    return new OutboxRelay(jdbc, transactions, publisher, Clock.fixed(now, ZoneOffset.UTC));
  }

  @BeforeEach
  void emptyOutbox() {
    jdbc.sql("delete from outbox").update();
  }

  /** Appends a pending row as step 7 does; returns its id. */
  long pending(UUID aggregateId, String eventType, String payload) {
    return jdbc.sql(
            "insert into outbox (aggregate_type, aggregate_id, event_type, payload, occurred_at)"
                + " values ('HelpRequest', :aggregateId, :eventType, cast(:payload as jsonb), now())"
                + " returning id")
        .param("aggregateId", aggregateId)
        .param("eventType", eventType)
        .param("payload", payload)
        .query(Long.class)
        .single();
  }

  /** The delivery columns of a row (ADR0015). */
  record Delivery(
      boolean published,
      int attempts,
      String lastError,
      Instant nextAttemptAt,
      Instant deadLetteredAt) {}

  Delivery delivery(long id) {
    return jdbc.sql(
            "select published_at, attempts, last_error, next_attempt_at, dead_lettered_at"
                + " from outbox where id = :id")
        .param("id", id)
        .query(
            (rs, n) ->
                new Delivery(
                    rs.getObject("published_at") != null,
                    rs.getInt("attempts"),
                    rs.getString("last_error"),
                    instant(rs.getObject("next_attempt_at", OffsetDateTime.class)),
                    instant(rs.getObject("dead_lettered_at", OffsetDateTime.class))))
        .single();
  }

  private static Instant instant(OffsetDateTime time) {
    return time == null ? null : time.toInstant();
  }

  Instant publishedAt(long id) {
    return instant(
        jdbc.sql("select published_at from outbox where id = :id")
            .param("id", id)
            .query((rs, n) -> rs.getObject("published_at", OffsetDateTime.class))
            .single());
  }

  boolean isPublished(long id) {
    return jdbc.sql("select published_at is not null from outbox where id = :id")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  @DisplayName(
      "one pending row -> published once (the fake received it) and published_at set -- B3-S01 US-CA-03 ADR0015")
  @Test
  void pendingRowIsPublishedOnce() {
    long id = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));

    relay().runOnce();

    assertThat(publisher.published())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message.channel()).isEqualTo("cooking-assistance.help-request.created"));
    assertThat(isPublished(id)).isTrue();
  }

  @DisplayName("a published row is not sent again -- B3-S01 US-CA-03 ADR0015")
  @Test
  void publishedRowIsNotSentAgain() {
    pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));

    relay().runOnce();
    relay().runOnce();

    assertThat(publisher.published()).hasSize(1);
  }

  @DisplayName(
      "a failing row of aggregate A holds back A's later row in that run; aggregate B goes on -- B3-S01,B3-S02 US-CA-03 ADR0015")
  @Test
  void failingRowHoldsBackItsAggregateOnly() {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    long a2 = pending(A, "HelpProvided", OutboxPayloads.helpProvided(A));
    long b1 = pending(B, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(B));
    publisher.rejectNext();

    relay().runOnce();

    assertThat(publisher.published())
        .extracting(message -> message.headers().get("correlationId"))
        .containsExactly(B.toString());
    assertThat(List.of(isPublished(a1), isPublished(a2), isPublished(b1)))
        .containsExactly(false, false, true);
  }

  @DisplayName(
      "while relay 1 publishes A1, a row A2 appended for the same help request is not published by relay 2 before A1 -- B3-S01,B3-S02 US-CA-03 ADR0015")
  @Test
  void laterRowDoesNotOvertakeAcrossRelays() throws Exception {
    pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    publisher.blockNext(entered, release);
    ExecutorService relay1 = Executors.newSingleThreadExecutor();
    Future<?> firstRun = relay1.submit(() -> relay().runOnce());
    assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
    pending(A, "HelpProvided", OutboxPayloads.helpProvided(A));

    relay().runOnce();
    List<OutgoingMessage> whileA1IsInFlight = publisher.published();
    release.countDown();
    firstRun.get(10, TimeUnit.SECONDS);
    relay1.shutdown();
    relay().runOnce();

    assertThat(whileA1IsInFlight).isEmpty();
    assertThat(publisher.published())
        .extracting(OutgoingMessage::channel)
        .containsExactly(
            "cooking-assistance.help-request.created", "cooking-assistance.help.provided");
  }

  @DisplayName(
      "an aggregate's two pending rows both go out in one run, in order -- B3-S01,B3-S02 US-CA-03 ADR0015")
  @Test
  void aggregateDrainsInOrderInOneRun() {
    pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    pending(A, "HelpProvided", OutboxPayloads.helpProvided(A));

    relay().runOnce();

    assertThat(publisher.published())
        .extracting(OutgoingMessage::channel)
        .containsExactly(
            "cooking-assistance.help-request.created", "cooking-assistance.help.provided");
  }

  @DisplayName(
      "two relays at once, fake publisher with delay -> every row published exactly once -- B3-S01 US-CA-03 ADR0015")
  @Test
  void severalRelaysPublishEachRowOnce() throws Exception {
    for (int i = 0; i < 10; i++) {
      UUID helpRequest = UUID.randomUUID();
      pending(helpRequest, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(helpRequest));
    }
    publisher.delayEachPublish(50);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService relays = Executors.newFixedThreadPool(2);
    List<Future<?>> runs =
        List.of(
            relays.submit(() -> startTogether(start)), relays.submit(() -> startTogether(start)));

    start.countDown();
    for (Future<?> run : runs) {
      run.get(30, TimeUnit.SECONDS);
    }
    relays.shutdown();

    assertThat(publisher.published()).hasSize(10);
    assertThat(publisher.published())
        .extracting(OutgoingMessage::messageId)
        .doesNotHaveDuplicates();
  }

  private void startTogether(CountDownLatch start) {
    try {
      start.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    relay().runOnce();
  }

  @DisplayName(
      "R1 the broker rejects A1 at 12:00:00 -> row pending, attempts 1, last_error 'broker rejected the message', next_attempt_at 12:00:01; a run at 12:00:00.5 sends nothing -- B3-S01 US-CA-03 ADR0015 L5")
  @Test
  void brokerFailureSchedulesTheNextAttempt() {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    publisher.rejectNext();

    relayAt(NOON).runOnce();
    Delivery afterFailure = delivery(a1);
    relayAt(NOON.plusMillis(500)).runOnce();

    assertThat(afterFailure)
        .isEqualTo(
            new Delivery(false, 1, "broker rejected the message", NOON.plusSeconds(1), null));
    assertThat(publisher.published()).isEmpty();
  }

  @DisplayName(
      "R2 confirm of A1 lost at 12:00:00; a run at 12:00:01 sends it again with the same messageId and marks it published at 12:00:01 (replaces the increment-0 'next run sends it again' item) -- B3-S01 US-CA-03 ADR0015 L5")
  @Test
  void dueRetryIsPublishedWithTheSameMessageId() {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    publisher.loseConfirmOfNext();

    relayAt(NOON).runOnce();
    relayAt(NOON.plusSeconds(1)).runOnce();

    assertThat(publisher.published())
        .hasSize(2)
        .extracting(OutgoingMessage::messageId)
        .containsOnly(publisher.published().getFirst().messageId());
    assertThat(publishedAt(a1)).isEqualTo(NOON.plusSeconds(1));
  }

  @DisplayName(
      "R3 consecutive failures 1..8 -> next attempt after 1, 2, 4, 8, 16, 32, 60, 60 s (parameterized) -- B3-S01 US-CA-03 ADR0015 L5")
  @ParameterizedTest(name = "failure {0} -> next attempt after {1} s")
  @CsvSource({"1, 1", "2, 2", "3, 4", "4, 8", "5, 16", "6, 32", "7, 60", "8, 60"})
  void backoffDoublesUpToSixtySeconds(int failure, long seconds) {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    jdbc.sql("update outbox set attempts = :earlier where id = :id")
        .param("earlier", failure - 1)
        .param("id", a1)
        .update();
    publisher.rejectNext();

    relayAt(NOON).runOnce();

    assertThat(delivery(a1).nextAttemptAt()).isEqualTo(NOON.plusSeconds(seconds));
  }

  @DisplayName(
      "R4 twelve broker failures in a row -> row still pending (attempts 12), dead_lettered_at empty -- B3-S01 US-CA-03 ADR0015 L5 QS-04")
  @Test
  void brokerFailuresNeverDeadLetter() {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));

    for (int hour = 0; hour < 12; hour++) {
      publisher.rejectNext();
      relayAt(NOON.plusSeconds(3600L * hour)).runOnce();
    }

    Delivery afterTwelveFailures = delivery(a1);
    assertThat(afterTwelveFailures.published()).isFalse();
    assertThat(afterTwelveFailures.attempts()).isEqualTo(12);
    assertThat(afterTwelveFailures.deadLetteredAt()).isNull();
    assertThat(publisher.published()).isEmpty();
  }

  @DisplayName(
      "R5 a HelpRequestCancelled row without helpRequest -> dead_lettered_at 12:00:00, last_error names the violation, nothing sent, attempts 0 -- B3-S03 US-CA-03 ADR0015 L5")
  @Test
  void specBreakingRowIsDeadLetteredAtOnce() {
    String withoutHelpRequest =
        OutboxPayloads.helpRequestCancelled(A).replace("\"helpRequest\":\"" + A + "\",", "");
    long a1 = pending(A, "HelpRequestCancelled", withoutHelpRequest);

    relayAt(NOON).runOnce();

    Delivery afterRun = delivery(a1);
    assertThat(afterRun.deadLetteredAt()).isEqualTo(NOON);
    assertThat(afterRun.attempts()).isZero();
    assertThat(afterRun.published()).isFalse();
    assertThat(afterRun.lastError()).contains("required property 'helpRequest' not found");
    assertThat(publisher.published()).isEmpty();
  }

  @DisplayName(
      "R6 an event type the spec does not know ('CopyBorrowed', aggregate A) and a pending row of B -> A's row dead-lettered, B's row published in the same run -- B3-S01 US-CA-03 ADR0015 L5")
  @Test
  void unknownEventTypeIsDeadLetteredAndTheRunGoesOn() {
    long a1 = pending(A, "CopyBorrowed", "{}");
    long b1 = pending(B, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(B));

    relayAt(NOON).runOnce();

    Delivery unknown = delivery(a1);
    assertThat(unknown.deadLetteredAt()).isEqualTo(NOON);
    assertThat(unknown.attempts()).isZero();
    assertThat(unknown.lastError())
        .isEqualTo("no message of the AsyncAPI spec carries CopyBorrowed");
    assertThat(isPublished(b1)).isTrue();
    assertThat(publisher.published())
        .extracting(message -> message.headers().get("correlationId"))
        .containsExactly(B.toString());
  }

  @DisplayName(
      "R7 A1 breaks the spec, A2 is valid -> A1 dead-lettered, A2 published in the same run -- B3-S03 US-CA-03 ADR0015 L5")
  @Test
  void deadLetterUnblocksItsHelpRequest() {
    String withoutHelpRequest =
        OutboxPayloads.helpRequestCancelled(A).replace("\"helpRequest\":\"" + A + "\",", "");
    long a1 = pending(A, "HelpRequestCancelled", withoutHelpRequest);
    long a2 = pending(A, "HelpProvided", OutboxPayloads.helpProvided(A));

    relayAt(NOON).runOnce();

    assertThat(delivery(a1).deadLetteredAt()).isEqualTo(NOON);
    assertThat(isPublished(a2)).isTrue();
    assertThat(publisher.published())
        .extracting(OutgoingMessage::channel)
        .containsExactly("cooking-assistance.help.provided");
  }

  @DisplayName(
      "R8 A1 waits for its next attempt (12:00:01); a run at 12:00:00.5 -> A2 not sent, B1 sent -- B3-S01,B3-S02 US-CA-03 ADR0015")
  @Test
  void waitingRowHoldsBackItsHelpRequestAcrossRuns() {
    long a1 = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    jdbc.sql("update outbox set attempts = 1, next_attempt_at = :next where id = :id")
        .param("next", OffsetDateTime.ofInstant(NOON.plusSeconds(1), ZoneOffset.UTC))
        .param("id", a1)
        .update();
    long a2 = pending(A, "HelpProvided", OutboxPayloads.helpProvided(A));
    long b1 = pending(B, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(B));

    relayAt(NOON.plusMillis(500)).runOnce();

    assertThat(List.of(isPublished(a1), isPublished(a2), isPublished(b1)))
        .containsExactly(false, false, true);
    assertThat(publisher.published())
        .extracting(message -> message.headers().get("correlationId"))
        .containsExactly(B.toString());
  }

  @DisplayName(
      "R9 a dead-lettered row -> one ERROR log line with row id, event type HelpRequestCancelled and the violation -- B3-S03 US-CA-03 ADR0015 L5")
  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void deadLetterIsLoggedWithRowIdEventTypeAndReason(CapturedOutput output) {
    String withoutHelpRequest =
        OutboxPayloads.helpRequestCancelled(A).replace("\"helpRequest\":\"" + A + "\",", "");
    long a1 = pending(A, "HelpRequestCancelled", withoutHelpRequest);

    relayAt(NOON).runOnce();

    assertThat(output.getOut().lines().filter(line -> line.contains("ERROR")))
        .singleElement()
        .asString()
        .contains(
            String.valueOf(a1),
            "HelpRequestCancelled",
            "required property 'helpRequest' not found");
  }
}
