package org.larder.cookingassistance.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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

  OutboxRelay relay() {
    return new OutboxRelay(jdbc, transactions, publisher);
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
      "publisher fails after sending (confirm lost) -> row stays pending; the next run sends it again with the same messageId -- B3-S01 US-CA-03 ADR0015")
  @Test
  void lostConfirmIsPublishedAgainWithTheSameMessageId() {
    long id = pending(A, "HelpRequestCreated", OutboxPayloads.helpRequestCreated(A));
    publisher.loseConfirmOfNext();

    relay().runOnce();
    assertThat(isPublished(id)).isFalse();
    relay().runOnce();

    assertThat(publisher.published())
        .hasSize(2)
        .extracting(OutgoingMessage::messageId)
        .containsOnly(publisher.published().getFirst().messageId());
    assertThat(isPublished(id)).isTrue();
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
}
