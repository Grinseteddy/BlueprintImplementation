package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Help Request repository against PostgreSQL 16 (Testcontainers) — increment 0, walking skeleton
 * (US-CA-03, B3-S01 + B3-S02). ADR0009 (schema cooking_assistance, Spring Data JDBC, Flyway; answer
 * as jsonb), ADR0010 (optimistic locking), ADR0015 (transactional outbox). No uniqueness rule in
 * the invariants sheet, so no unique constraint is tested.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class HelpRequestRepositoryIT {

  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository repository;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @DisplayName(
      "the V1 migration creates help_request, asked_provider, help, outbox and processed_message in schema cooking_assistance on an empty database -- B3-S01 US-CA-03 ADR0009")
  @Test
  void migrationCreatesTheTables() {
    List<String> tables =
        jdbc.sql(
                "select table_name from information_schema.tables"
                    + " where table_schema = 'cooking_assistance'")
            .query(String.class)
            .list();

    assertThat(tables)
        .contains("help_request", "asked_provider", "help", "outbox", "processed_message");
  }

  @DisplayName(
      "findById of an unknown help request id returns Optional.empty(), not an error -- B3-S01 US-CA-03")
  @Test
  void unknownIdIsAbsent() {
    Optional<HelpRequest> found =
        repository.findById(
            new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000001")));

    assertThat(found).isEmpty();
  }

  @DisplayName(
      "Lena's new OPEN request to Grandma Avatar about step 4 is saved and found again with every field equal: id, requester, title, description, type PREPARATION_STEP_EXPLANATION, recipe, step, asked provider (type, deadline Sat 06:00, ASKED), status OPEN, no helps -- B3-S01 US-CA-03 INV-01 INV-02 INV-09")
  @Test
  void newOpenRequestRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.id()).isEqualTo(HelpRequests.HELP_REQUEST_ID);
              assertThat(found.requester()).isEqualTo(HelpRequests.LENA);
              assertThat(found.title()).isEqualTo("How do I keep the sauce from splitting?");
              assertThat(found.description()).isEqualTo("Step 4 of my Hollandaise keeps splitting");
              assertThat(found.type()).isEqualTo(HelpType.PREPARATION_STEP_EXPLANATION);
              assertThat(found.recipe()).isEqualTo(HelpRequests.HOLLANDAISE);
              assertThat(found.howToStep()).isEqualTo(HelpRequests.STEP_4);
              assertThat(found.askedProviders())
                  .containsExactly(
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR,
                          Instant.parse("2026-11-07T05:00:00Z"),
                          ProviderState.ASKED));
              assertThat(found.status()).isEqualTo(HelpRequestStatus.OPEN);
              assertThat(found.helps()).isEmpty();
            });
  }

  @DisplayName(
      "a request asked of the Community and Grandma Avatar round-trips with both asked providers (two child rows) -- B3-S01 US-CA-03 INV-01 R1.E2")
  @Test
  void twoAskedProvidersRoundTrip() {
    HelpRequest request =
        HelpRequests.lenaAsksAboutStep4(
            HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found ->
                assertThat(found.askedProviders())
                    .containsExactlyElementsOf(request.askedProviders()));
  }

  @DisplayName(
      "an ANSWERED request with Grandma's Help (answer 'butter in a thin stream' with 3 photos) round-trips: help id, provider type GRANDMA_AVATAR, no person, answer title, answer incl. 3 images, not accepted; Grandma's side ANSWERED -- B3-S02 US-CA-03 INV-04 R4.E1")
  @Test
  void answeredRequestWithGrandmasHelpRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.status()).isEqualTo(HelpRequestStatus.ANSWERED);
              assertThat(found.askedProviders())
                  .extracting(AskedProvider::state)
                  .containsExactly(ProviderState.ANSWERED);
              assertThat(found.helps())
                  .singleElement()
                  .satisfies(
                      help -> {
                        assertThat(help.id()).isEqualTo(HelpRequests.HELP_ID);
                        assertThat(help.providerType()).isEqualTo(HelpProviderType.GRANDMA_AVATAR);
                        assertThat(help.provider()).isEmpty();
                        assertThat(help.answerTitle()).isEqualTo("Butter in a thin stream");
                        assertThat(help.answer()).isEqualTo(HelpRequests.BUTTER_WITH_THREE_PHOTOS);
                        assertThat(help.isAccepted()).isFalse();
                      });
            });
  }

  @DisplayName(
      "a Help from Community member Jonas round-trips with his person id -- B3-S02 US-CA-03 INV-04 R4.E3")
  @Test
  void helpFromACommunityMemberRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    HelpRequests.communityMemberExplainsStep4(request, HelpRequests.HELP_ID, HelpRequests.JONAS);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found ->
                assertThat(found.helps())
                    .singleElement()
                    .satisfies(
                        help -> {
                          assertThat(help.providerType()).isEqualTo(HelpProviderType.COMMUNITY);
                          assertThat(help.provider()).contains(HelpRequests.JONAS);
                        }));
  }

  @DisplayName("a loaded help request has no pending events -- B3-S01 US-CA-03 reconstitution")
  @Test
  void loadedRequestHasNoPendingEvents() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(found -> assertThat(found.pullEvents()).isEmpty());
  }

  @DisplayName(
      "saving a new request stores version 1; loading it, providing help and saving stores version 2 -- B3-S02 US-CA-03 ADR0010")
  @Test
  void versionStartsAtOneAndGrowsByOne() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    assertThat(storedVersion()).isEqualTo(1L);

    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(loaded);
    repository.save(loaded);

    assertThat(storedVersion()).isEqualTo(2L);
  }

  private long storedVersion() {
    return jdbc.sql("select version from help_request where id = :id")
        .param("id", HelpRequests.HELP_REQUEST_ID.value())
        .query(Long.class)
        .single();
  }

  @DisplayName(
      "optimistic locking: two loads of the same request, Jonas answers in one and Mia in the other, both saved -> the second save fails with ConcurrencyConflict and the stored request holds only Jonas's Help -- B3-S02 US-CA-03 ADR0010 QS-10")
  @Test
  void secondConcurrentSaveFailsWithConcurrencyConflict() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    HelpRequest loadedForJonas = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequest loadedForMia = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(
        loadedForJonas, HelpRequests.HELP_ID, HelpRequests.JONAS);
    HelpRequests.communityMemberExplainsStep4(
        loadedForMia, HelpRequests.OTHER_HELP_ID, HelpRequests.MIA);

    repository.save(loadedForJonas);

    assertThatThrownBy(() -> repository.save(loadedForMia)).isInstanceOf(ConcurrencyConflict.class);
    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            stored ->
                assertThat(stored.helps())
                    .extracting(help -> help.provider().orElseThrow())
                    .containsExactly(HelpRequests.JONAS));
  }

  @DisplayName(
      "saving a new request appends one outbox row: aggregate_type HelpRequest, aggregate_id = request id, event_type HelpRequestCreated, payload with the AsyncAPI fields (helpRequestId, requester, title, type, description, recipe, howToStep, askedProviders[providerType, deadline], status OPEN, occurredAt), published_at null, attempts 0, next_attempt_at -infinity, last_error null, dead_lettered_at null -- B3-S01 US-CA-03 ADR0015 AsyncAPI HelpRequestCreated")
  @Test
  void savingANewRequestAppendsHelpRequestCreatedToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    List<OutboxRow> rows = outboxRows();
    assertThat(rows).hasSize(1);
    OutboxRow row = rows.getFirst();
    assertThat(row.aggregateType()).isEqualTo("HelpRequest");
    assertThat(row.aggregateId()).isEqualTo(HelpRequests.HELP_REQUEST_ID.value());
    assertThat(row.eventType()).isEqualTo("HelpRequestCreated");
    assertThat(row.publishedAt()).isNull();
    assertThat(row.attempts()).isZero();
    assertThat(row.nextAttemptAt()).isEqualTo("-infinity");
    assertThat(row.lastError()).isNull();
    assertThat(row.deadLetteredAt()).isNull();
    JsonNode payload = new ObjectMapper().readTree(row.payload());
    assertThat(payload.get("helpRequestId").asText())
        .isEqualTo(HelpRequests.HELP_REQUEST_ID.value().toString());
    assertThat(payload.get("requester").asText()).isEqualTo(HelpRequests.LENA.value().toString());
    assertThat(payload.get("title").asText()).isEqualTo("How do I keep the sauce from splitting?");
    assertThat(payload.get("type").asText()).isEqualTo("PREPARATION_STEP_EXPLANATION");
    assertThat(payload.get("description").asText())
        .isEqualTo("Step 4 of my Hollandaise keeps splitting");
    assertThat(payload.get("recipe").asText())
        .isEqualTo(HelpRequests.HOLLANDAISE.value().toString());
    assertThat(payload.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
    assertThat(payload.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.get("askedProviders").get(0).get("deadline").asText())
        .isEqualTo("2026-11-07T05:00:00Z");
    assertThat(payload.get("status").asText()).isEqualTo("OPEN");
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-06T17:00:00Z");
  }

  /** An outbox row as stored, timestamps of the delivery columns as text. */
  record OutboxRow(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      String payload,
      String publishedAt,
      int attempts,
      String nextAttemptAt,
      String lastError,
      String deadLetteredAt) {}

  private List<OutboxRow> outboxRows() {
    return jdbc.sql(
            "select aggregate_type, aggregate_id, event_type, payload::text as payload,"
                + " published_at::text as published_at, attempts,"
                + " next_attempt_at::text as next_attempt_at, last_error,"
                + " dead_lettered_at::text as dead_lettered_at from outbox order by id")
        .query(
            (rs, n) ->
                new OutboxRow(
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("payload"),
                    rs.getString("published_at"),
                    rs.getInt("attempts"),
                    rs.getString("next_attempt_at"),
                    rs.getString("last_error"),
                    rs.getString("dead_lettered_at")))
        .list();
  }

  @DisplayName(
      "saving after Grandma provided help appends one outbox row with event_type HelpProvided and the AsyncAPI payload (helpId, helpRequest, helpRequester, answerTitle, helpProviderType, answer, helpRequestStatus ANSWERED, occurredAt; no helpProvider for Grandma) -- B3-S02 US-CA-03 ADR0015 AsyncAPI HelpProvided")
  @Test
  void savingAfterProvideHelpAppendsHelpProvidedToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(loaded);

    repository.save(loaded);

    List<OutboxRow> provided =
        outboxRows().stream().filter(row -> row.eventType().equals("HelpProvided")).toList();
    assertThat(provided).hasSize(1);
    assertThat(provided.getFirst().aggregateId()).isEqualTo(HelpRequests.HELP_REQUEST_ID.value());
    JsonNode payload = new ObjectMapper().readTree(provided.getFirst().payload());
    assertThat(payload.get("helpId").asText()).isEqualTo(HelpRequests.HELP_ID.value().toString());
    assertThat(payload.get("helpRequest").asText())
        .isEqualTo(HelpRequests.HELP_REQUEST_ID.value().toString());
    assertThat(payload.get("helpRequester").asText())
        .isEqualTo(HelpRequests.LENA.value().toString());
    assertThat(payload.get("answerTitle").asText()).isEqualTo("Butter in a thin stream");
    assertThat(payload.get("helpProviderType").asText()).isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.has("helpProvider")).isFalse();
    JsonNode answer = payload.get("answer");
    assertThat(answer.get("answerType").asText()).isEqualTo("PREPARATION_STEP_EXPLANATION");
    assertThat(answer.get("recipe").asText())
        .isEqualTo(HelpRequests.HOLLANDAISE.value().toString());
    assertThat(answer.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
    assertThat(answer.get("description").asText()).isEqualTo("Add the butter in a thin stream");
    assertThat(answer.get("images")).hasSize(3);
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("ANSWERED");
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-06T17:10:00Z");
  }

  @DisplayName(
      "a save that fails with ConcurrencyConflict leaves neither its state change nor its outbox row -- B3-S02 US-CA-03 ADR0010 ADR0015")
  @Test
  void failedSaveLeavesNoStateAndNoOutboxRow() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    HelpRequest loadedForJonas = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequest loadedForMia = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(
        loadedForJonas, HelpRequests.HELP_ID, HelpRequests.JONAS);
    HelpRequests.communityMemberExplainsStep4(
        loadedForMia, HelpRequests.OTHER_HELP_ID, HelpRequests.MIA);
    repository.save(loadedForJonas);

    assertThatThrownBy(() -> repository.save(loadedForMia)).isInstanceOf(ConcurrencyConflict.class);

    assertThat(outboxRows())
        .extracting(OutboxRow::eventType)
        .containsExactly("HelpRequestCreated", "HelpProvided");
    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .extracting(help -> help.id())
        .containsExactly(HelpRequests.HELP_ID);
  }

  @DisplayName(
      "a second Help added to a stored request (Jonas, then Mia) round-trips with both Helps and leaves no orphan help rows -- B3-S02 US-CA-03 INV-04 R4.E3 child rows")
  @Test
  void addedHelpRoundTripsWithoutOrphans() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    HelpRequest first = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(first, HelpRequests.HELP_ID, HelpRequests.JONAS);
    repository.save(first);
    HelpRequest second = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(second, HelpRequests.OTHER_HELP_ID, HelpRequests.MIA);

    repository.save(second);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .extracting(help -> help.provider().orElseThrow())
        .containsExactly(HelpRequests.JONAS, HelpRequests.MIA);
    assertThat(jdbc.sql("select count(*) from help").query(Integer.class).single()).isEqualTo(2);
  }

  @DisplayName(
      "the V2 migration adds help.rejected (boolean, not null, default false) -- B3-S05 US-CA-03 INV-39 ADR0009")
  @Test
  void v2MigrationAddsTheRejectedMark() {
    List<String> column =
        jdbc.sql(
                "select data_type || ' ' || is_nullable || ' ' || column_default"
                    + " from information_schema.columns where table_schema = 'cooking_assistance'"
                    + " and table_name = 'help' and column_name = 'rejected'")
            .query(String.class)
            .list();

    assertThat(column).containsExactly("boolean NO false");
  }

  @DisplayName(
      "Lena cancelled her open request -> found again CANCELLED with Grandma's side unchanged (ASKED, deadline Sat 06:00) -- B3-S03 US-CA-03 INV-08 state CANCELLED")
  @Test
  void cancelledRequestRoundTrips() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);

    repository.save(loaded);

    HelpRequest found = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(found.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR,
                Instant.parse("2026-11-07T05:00:00Z"),
                ProviderState.ASKED));
  }

  @DisplayName(
      "Lena accepted Grandma's Help -> found again CLOSED with the Help accepted and not rejected; one help row -- B3-S04 US-CA-03 INV-05 state CLOSED, changed child row")
  @Test
  void closedRequestWithAnAcceptedHelpRoundTrips() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository.save(answered);
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);

    repository.save(loaded);

    HelpRequest found = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(found.helps())
        .singleElement()
        .satisfies(
            help -> {
              assertThat(help.isAccepted()).isTrue();
              assertThat(help.isRejected()).isFalse();
            });
    assertThat(jdbc.sql("select count(*) from help").query(Integer.class).single()).isEqualTo(1);
  }

  @DisplayName(
      "Grandma answered, Lena rejected (Fri 20:00), Grandma answered again, Lena accepted -> found again CLOSED with two Helps [first rejected, second accepted], Grandma's side ANSWERED with deadline Sat 08:00; two help rows -- B3-S04,B3-S05 US-CA-03 INV-06 INV-39 changed child rows")
  @Test
  void rejectedThenAcceptedHelpsRoundTripWithoutOrphans() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository.save(answered);
    HelpRequest rejected = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    rejected.reject(HelpRequests.HELP_ID, "warm water didn't help", utc("2026-11-06T19:00:00Z"));
    repository.save(rejected);
    HelpRequest answeredAgain = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    answeredAgain.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        utc("2026-11-06T20:00:00Z"));
    repository.save(answeredAgain);
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.accept(HelpRequests.OTHER_HELP_ID, utc("2026-11-06T20:30:00Z"));

    repository.save(loaded);

    HelpRequest found = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(found.helps())
        .extracting(Help::id, Help::isRejected, Help::isAccepted)
        .containsExactly(
            tuple(HelpRequests.HELP_ID, true, false),
            tuple(HelpRequests.OTHER_HELP_ID, false, true));
    assertThat(found.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR,
                Instant.parse("2026-11-07T07:00:00Z"),
                ProviderState.ANSWERED));
    assertThat(jdbc.sql("select count(*) from help").query(Integer.class).single()).isEqualTo(2);
  }

  private static Clock utc(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  @DisplayName(
      "only Community asked, timed out at Sat 06:00:01 -> found again CANCELLED with Community's side TIMED_OUT -- B3-S06 US-CA-03 INV-09 R8.E1 state TIMED_OUT")
  @Test
  void timedOutRequestRoundTrips() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.timeOut(utc("2026-11-07T05:00:01Z"));

    repository.save(loaded);

    HelpRequest found = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(found.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.COMMUNITY,
                Instant.parse("2026-11-07T05:00:00Z"),
                ProviderState.TIMED_OUT));
  }

  @DisplayName(
      "saving after Lena cancelled appends one outbox row HelpRequestCancelled with helpRequest, helpRequester Lena, note, helpRequestStatus CANCELLED, occurredAt 2026-11-06T17:10:00Z, delivery columns at their start values -- B3-S03 US-CA-03 ADR0015 AsyncAPI HelpRequestCancelled")
  @Test
  void savingAfterCancelAppendsHelpRequestCancelledToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.cancel(Optional.of("an ice cube saved it"), HelpRequests.FRIDAY_18_10);

    repository.save(loaded);

    List<OutboxRow> rows = outboxRows();
    assertThat(rows)
        .extracting(OutboxRow::eventType)
        .containsExactly("HelpRequestCreated", "HelpRequestCancelled");
    OutboxRow row = rows.getLast();
    assertThat(row.aggregateId()).isEqualTo(HelpRequests.HELP_REQUEST_ID.value());
    assertThat(row.publishedAt()).isNull();
    assertThat(row.attempts()).isZero();
    assertThat(row.nextAttemptAt()).isEqualTo("-infinity");
    JsonNode payload = new ObjectMapper().readTree(row.payload());
    assertThat(payload.get("helpRequest").asText())
        .isEqualTo(HelpRequests.HELP_REQUEST_ID.value().toString());
    assertThat(payload.get("helpRequester").asText())
        .isEqualTo(HelpRequests.LENA.value().toString());
    assertThat(payload.get("note").asText()).isEqualTo("an ice cube saved it");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-06T17:10:00Z");
  }

  @DisplayName(
      "saving after Lena accepted Grandma's Help appends one outbox row HelpAccepted with helpId, helpProviderType GRANDMA_AVATAR, helpRequestStatus CLOSED -- B3-S04 US-CA-03 ADR0015 AsyncAPI HelpAccepted")
  @Test
  void savingAfterAcceptAppendsHelpAcceptedToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository.save(answered);
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);

    repository.save(loaded);

    List<OutboxRow> rows = outboxRows();
    assertThat(rows)
        .extracting(OutboxRow::eventType)
        .containsExactly("HelpRequestCreated", "HelpProvided", "HelpAccepted");
    JsonNode payload = new ObjectMapper().readTree(rows.getLast().payload());
    assertThat(payload.get("helpId").asText()).isEqualTo(HelpRequests.HELP_ID.value().toString());
    assertThat(payload.get("helpProviderType").asText()).isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("CLOSED");
  }

  @DisplayName(
      "saving after Lena rejected Grandma's Help at Fri 20:00 appends one outbox row HelpRejected with helpId, note, askedProviders[GRANDMA_AVATAR, deadline 2026-11-07T07:00:00Z], helpRequestStatus OPEN -- B3-S05 US-CA-03 ADR0015 AsyncAPI HelpRejected")
  @Test
  void savingAfterRejectAppendsHelpRejectedToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository.save(answered);
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.reject(HelpRequests.HELP_ID, "warm water didn't help", utc("2026-11-06T19:00:00Z"));

    repository.save(loaded);

    List<OutboxRow> rows = outboxRows();
    assertThat(rows)
        .extracting(OutboxRow::eventType)
        .containsExactly("HelpRequestCreated", "HelpProvided", "HelpRejected");
    JsonNode payload = new ObjectMapper().readTree(rows.getLast().payload());
    assertThat(payload.get("helpId").asText()).isEqualTo(HelpRequests.HELP_ID.value().toString());
    assertThat(payload.get("note").asText()).isEqualTo("warm water didn't help");
    assertThat(payload.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.get("askedProviders").get(0).get("deadline").asText())
        .isEqualTo("2026-11-07T07:00:00Z");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("OPEN");
  }

  @DisplayName(
      "saving after the request timed out appends one outbox row HelpRequestTimedOut with timedOutProviders [COMMUNITY], suggestedProviders [GRANDMA_AVATAR], helpRequestStatus CANCELLED, occurredAt 2026-11-07T05:00:01Z -- B3-S06 US-CA-03 ADR0015 AsyncAPI HelpRequestTimedOut")
  @Test
  void savingAfterTimeOutAppendsHelpRequestTimedOutToTheOutbox() throws Exception {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.timeOut(utc("2026-11-07T05:00:01Z"));

    repository.save(loaded);

    List<OutboxRow> rows = outboxRows();
    assertThat(rows)
        .extracting(OutboxRow::eventType)
        .containsExactly("HelpRequestCreated", "HelpRequestTimedOut");
    JsonNode payload = new ObjectMapper().readTree(rows.getLast().payload());
    assertThat(payload.get("timedOutProviders").toString()).isEqualTo("[\"COMMUNITY\"]");
    assertThat(payload.get("suggestedProviders").toString()).isEqualTo("[\"GRANDMA_AVATAR\"]");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-07T05:00:01Z");
  }

  @DisplayName(
      "a due request claimed in an open transaction -> a second transaction's findDueForTimeOut skips it (FOR UPDATE SKIP LOCKED) and returns it once the first commits without a time-out -- B3-S06 US-CA-03 ADR0021 QS-09 (step 7 r1 from step 8)")
  @Test
  void claimedDueRequestsAreSkippedByASecondClaim() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
    Instant saturday060001 = Instant.parse("2026-11-07T05:00:01Z");
    TransactionTemplate first = new TransactionTemplate(transactions);

    List<HelpRequestId> seenBySecondClaim =
        first.execute(
            claim -> {
              assertThat(repository.findDueForTimeOut(saturday060001, 10)).hasSize(1);
              return CompletableFuture.supplyAsync(
                      () ->
                          new TransactionTemplate(transactions)
                              .execute(
                                  second ->
                                      repository.findDueForTimeOut(saturday060001, 10).stream()
                                          .map(HelpRequest::id)
                                          .toList()))
                  .join();
            });

    assertThat(seenBySecondClaim).isEmpty();
    assertThat(repository.findDueForTimeOut(saturday060001, 10))
        .extracting(HelpRequest::id)
        .containsExactly(HelpRequests.HELP_REQUEST_ID);
  }
}
