package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
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
}
