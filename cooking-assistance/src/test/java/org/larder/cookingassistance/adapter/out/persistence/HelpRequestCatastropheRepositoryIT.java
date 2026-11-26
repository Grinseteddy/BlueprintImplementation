package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CatastropheMitigation;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
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
 * Catastrophe requests in the Help Request repository against PostgreSQL 16 (Testcontainers) --
 * increment 3 (US-CA-04, B4-S01, B4-S02, B3-S06). Optimistic locking and failed saves do not depend
 * on the kind of help and stay in HelpRequestRepositoryIT.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class HelpRequestCatastropheRepositoryIT {

  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository repository;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  // B4-S01: Request help Catastrophe

  @DisplayName(
      "the V10 migration adds help_request.pictures (text[], not null, default empty); requests saved before have none -- B4-S01 US-CA-04 INV-20 INV-38 ADR0009 (decided 2026-11-13: text[])")
  @Test
  void v10AddsThePicturesColumn() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    List<String> columns =
        jdbc.sql(
                "select column_name || ' ' || data_type || ' ' || is_nullable || ' '"
                    + " || coalesce(column_default, '')"
                    + " from information_schema.columns"
                    + " where table_schema = 'cooking_assistance' and table_name = 'help_request'"
                    + " and column_name = 'pictures'")
            .query(String.class)
            .list();

    assertThat(columns).containsExactly("pictures ARRAY NO '{}'::text[]");
    assertThat(
            jdbc.sql("select cardinality(pictures) from help_request")
                .query(Integer.class)
                .single())
        .isZero();
  }

  @DisplayName(
      "Lena's OPEN request about her burnt roast chicken, recipe Roast chicken, photos [burnt-chicken-1, burnt-chicken-2], asking Community and Grandma, is found again with every field equal: type STEPS_TO_MITIGATE_CATASTROPHE, recipe, pictures in order, Community until 18:55 and Grandma until 18:45 (both ASKED), no step, no ingredient, status OPEN, no helps -- B4-S01 US-CA-04 INV-19 INV-20 INV-26 R1.E2")
  @Test
  void newCatastropheRequestRoundTrips() {
    repository.save(
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.of(HelpRequests.ROAST_CHICKEN),
            HelpRequests.BURNT_CHICKEN_PHOTOS,
            HelpProviderType.COMMUNITY,
            HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.type()).isEqualTo(HelpType.STEPS_TO_MITIGATE_CATASTROPHE);
              assertThat(found.recipe()).isEqualTo(HelpRequests.ROAST_CHICKEN);
              assertThat(found.pictures()).isEqualTo(HelpRequests.BURNT_CHICKEN_PHOTOS);
              assertThat(found.askedProviders())
                  .containsExactlyInAnyOrder(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(15))),
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(5))));
              assertThat(found.howToStep()).isNull();
              assertThat(found.ingredient()).isNull();
              assertThat(found.status()).isEqualTo(HelpRequestStatus.OPEN);
              assertThat(found.helps()).isEmpty();
            });
  }

  @DisplayName(
      "a catastrophe request without a recipe and without photos is found again with recipe empty and no pictures -- B4-S01 US-CA-04 INV-19 INV-20")
  @Test
  void aCatastropheWithoutRecipeOrPhotosRoundTrips() {
    repository.save(
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.empty(), List.of(), HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.type()).isEqualTo(HelpType.STEPS_TO_MITIGATE_CATASTROPHE);
              assertThat(found.recipe()).isNull();
              assertThat(found.pictures()).isEmpty();
            });
  }

  @DisplayName(
      "saving Lena's new catastrophe request appends one outbox row HelpRequestCreated, aggregate HelpRequest, payload with type STEPS_TO_MITIGATE_CATASTROPHE, recipe and pictures, published_at empty, attempts 0 -- B4-S01 US-CA-04 INV-19 INV-20 ADR0015 AsyncAPI HelpRequestCreated")
  @Test
  void savingAppendsHelpRequestCreatedWithRecipeAndPictures() throws Exception {
    repository.save(
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.of(HelpRequests.ROAST_CHICKEN),
            HelpRequests.BURNT_CHICKEN_PHOTOS,
            HelpProviderType.GRANDMA_AVATAR));

    Map<String, Object> row = outboxRow("HelpRequestCreated");
    assertThat(row.get("aggregate_type")).isEqualTo("HelpRequest");
    assertThat(row.get("published_at")).isNull();
    assertThat(row.get("attempts")).isEqualTo(0);
    JsonNode payload = new ObjectMapper().readTree((String) row.get("payload"));
    assertThat(payload.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(payload.get("recipe").asText())
        .isEqualTo(HelpRequests.ROAST_CHICKEN.value().toString());
    assertThat(payload.get("pictures"))
        .extracting(JsonNode::asText)
        .containsExactly(
            "https://img.larder.example/burnt-chicken-1.jpg",
            "https://img.larder.example/burnt-chicken-2.jpg");
  }

  // B4-S02: Provide Help (catastrophe mitigation)

  @DisplayName(
      "Grandma's Help \"Save the chicken\" is found again with the same CatastropheMitigation: recipe Roast chicken, explanation \"Cut off the black skin and serve it with a sauce\", images [cut-skin, sauce] in order; status ANSWERED -- B4-S02 US-CA-04 INV-22 ADR0009 (answer as jsonb)")
  @Test
  void helpWithAMitigationRoundTrips() {
    HelpRequest request =
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.of(HelpRequests.ROAST_CHICKEN),
            HelpRequests.BURNT_CHICKEN_PHOTOS,
            HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaMitigates(request, HelpRequests.ROAST_CHICKEN);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.status()).isEqualTo(HelpRequestStatus.ANSWERED);
              assertThat(found.helps())
                  .singleElement()
                  .extracting(Help::answer)
                  .isEqualTo(
                      new CatastropheMitigation(
                          HelpRequests.ROAST_CHICKEN,
                          "Cut off the black skin and serve it with a sauce",
                          List.of(
                              URI.create("https://img.larder.example/cut-skin.jpg"),
                              URI.create("https://img.larder.example/sauce.jpg"))));
            });
  }

  @DisplayName(
      "a mitigation that names no recipe is found again with recipe empty -- B4-S02 US-CA-04 INV-37")
  @Test
  void aMitigationWithoutARecipeRoundTrips() {
    HelpRequest request =
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.of(HelpRequests.ROAST_CHICKEN),
            HelpRequests.BURNT_CHICKEN_PHOTOS,
            HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaMitigates(request, null);

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found ->
                assertThat(found.helps())
                    .singleElement()
                    .extracting(Help::answer)
                    .isInstanceOfSatisfying(
                        CatastropheMitigation.class,
                        mitigation -> assertThat(mitigation.recipe()).isNull()));
  }

  // B3-S06: Time out help request (per side, step 3 r21)

  @DisplayName(
      "Grandma's side timed out at 18:46 while the Community is still asked until 18:55 -> found again with Grandma TIMED_OUT, Community ASKED, status OPEN -- B3-S06 US-CA-04 INV-26 R9.E3 step 3 r21")
  @Test
  void aSideTimedOutAloneRoundTrips() {
    HelpRequest request = lenaAsksCommunityAndGrandma();
    request.timeOut(Clock.offset(HelpRequests.SATURDAY_18_40, Duration.ofMinutes(6)));

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.askedProviders())
                  .containsExactlyInAnyOrder(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(15)),
                          ProviderState.ASKED),
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(5)),
                          ProviderState.TIMED_OUT));
              assertThat(found.status()).isEqualTo(HelpRequestStatus.OPEN);
            });
  }

  @DisplayName(
      "saving after a time-out pass that timed out only Grandma's side appends no outbox row and stores version + 1 -- B3-S06 US-CA-04 INV-26 R9.E3 step 3 r21 ADR0015")
  @Test
  void aSideTimeOutWritesNoOutboxRow() {
    repository.save(lenaAsksCommunityAndGrandma());
    HelpRequest loaded = repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.timeOut(Clock.offset(HelpRequests.SATURDAY_18_40, Duration.ofMinutes(6)));

    repository.save(loaded);

    assertThat(jdbc.sql("select event_type from outbox order by id").query(String.class).list())
        .containsExactly("HelpRequestCreated");
    assertThat(
            jdbc.sql("select version from help_request where id = :id")
                .param("id", HelpRequests.HELP_REQUEST_ID.value())
                .query(Long.class)
                .single())
        .isEqualTo(2L);
  }

  /** The one outbox row of the given event type, its payload as text. */
  private Map<String, Object> outboxRow(String eventType) {
    return jdbc.sql(
            "select aggregate_type, payload::text as payload, published_at, attempts from outbox"
                + " where event_type = :eventType")
        .param("eventType", eventType)
        .query()
        .singleRow();
  }

  /** Lena's burnt-chicken request with recipe and photos, asking the Community and Grandma. */
  private static HelpRequest lenaAsksCommunityAndGrandma() {
    return HelpRequests.lenaAsksAboutBurntChicken(
        Optional.of(HelpRequests.ROAST_CHICKEN),
        HelpRequests.BURNT_CHICKEN_PHOTOS,
        HelpProviderType.COMMUNITY,
        HelpProviderType.GRANDMA_AVATAR);
  }
}
