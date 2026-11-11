package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpAnswer;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.Note;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteIngredient;
import org.larder.cookingassistance.domain.helprequest.SubstituteRejectedBefore;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.domain.helprequest.Unit;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Ingredient requests in the Help Request repository against PostgreSQL 16 (Testcontainers) --
 * increment 2 (US-CA-02, B2-S01, B2-S02). Optimistic locking and failed saves do not depend on the
 * kind of help and stay in HelpRequestRepositoryIT.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class HelpRequestIngredientRepositoryIT {

  /** Two hours after Lena asked. */
  private static final Clock MONDAY_20_00 =
      Clock.offset(HelpRequests.MONDAY_18_00, Duration.ofHours(2));

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

  // B2-S01: Request help ingredients

  @DisplayName(
      "the V7 migration adds the nullable columns ingredient (uuid) and substitute_requirements (text) to help_request, without a foreign key -- B2-S01 US-CA-02 INV-11 ADR0009")
  @Test
  void v7AddsTheIngredientColumns() {
    List<String> columns =
        jdbc.sql(
                "select column_name || ' ' || data_type || ' ' || is_nullable"
                    + " from information_schema.columns"
                    + " where table_schema = 'cooking_assistance' and table_name = 'help_request'")
            .query(String.class)
            .list();
    List<String> foreignKeys =
        jdbc.sql(
                "select conname from pg_constraint"
                    + " where conrelid = 'cooking_assistance.help_request'::regclass"
                    + " and contype = 'f'")
            .query(String.class)
            .list();

    assertThat(columns).contains("ingredient uuid YES", "substitute_requirements text YES");
    assertThat(foreignKeys).isEmpty();
  }

  @DisplayName(
      "Lena's new OPEN request about 200 g creme fraiche in tomato soup is saved and found again with every field equal: type INGREDIENT_SUBSTITUTE, recipe, ingredient, requirements \"200 g, not available\", no step, asked provider with deadline + 24 h, status OPEN, no helps -- B2-S01 US-CA-02 INV-11 R2.E1")
  @Test
  void newIngredientRequestRoundTrips() {
    repository.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.type()).isEqualTo(HelpType.INGREDIENT_SUBSTITUTE);
              assertThat(found.recipe()).isEqualTo(HelpRequests.TOMATO_SOUP);
              assertThat(found.ingredient()).isEqualTo(HelpRequests.CREME_FRAICHE);
              assertThat(found.substituteRequirements()).isEqualTo("200 g, not available");
              assertThat(found.howToStep()).isNull();
              assertThat(found.askedProviders())
                  .containsExactly(
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR,
                          HelpRequests.MONDAY_18_00.instant().plus(Duration.ofHours(24))));
              assertThat(found.status()).isEqualTo(HelpRequestStatus.OPEN);
              assertThat(found.helps()).isEmpty();
            });
  }

  @DisplayName(
      "a preparation-step request is found again with ingredient and substituteRequirements empty, and its columns are NULL -- B3-S01 US-CA-03 INV-02 (regression)")
  @Test
  void aPreparationStepRequestHasNoIngredient() {
    repository.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.ingredient()).isNull();
              assertThat(found.substituteRequirements()).isNull();
            });
    assertThat(
            jdbc.sql(
                    "select count(*) from help_request"
                        + " where ingredient is null and substitute_requirements is null")
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  @DisplayName("a loaded ingredient request has no pending events -- B2-S01 US-CA-02")
  @Test
  void loadedIngredientRequestHasNoPendingEvents() {
    repository.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(found -> assertThat(found.pullEvents()).isEmpty());
  }

  @DisplayName(
      "saving a new ingredient request appends one outbox row HelpRequestCreated, aggregate HelpRequest, payload with ingredient, substituteRequirements and recipe, no howToStep, published_at empty, attempts 0 -- B2-S01 US-CA-02 INV-11 ADR0015 AsyncAPI HelpRequestCreated")
  @Test
  void savingAppendsHelpRequestCreatedWithTheIngredient() throws Exception {
    repository.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR));

    Map<String, Object> row = outboxRow("HelpRequestCreated");
    assertThat(row.get("aggregate_type")).isEqualTo("HelpRequest");
    assertThat(row.get("published_at")).isNull();
    assertThat(row.get("attempts")).isEqualTo(0);
    JsonNode payload = new ObjectMapper().readTree((String) row.get("payload"));
    assertThat(payload.get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
    assertThat(payload.get("substituteRequirements").asText()).isEqualTo("200 g, not available");
    assertThat(payload.get("recipe").asText())
        .isEqualTo(HelpRequests.TOMATO_SOUP.value().toString());
    assertThat(payload.has("howToStep")).isFalse();
  }

  // B2-S02: Provide Help with substitutes

  @DisplayName(
      "Grandma's Help proposing 200 g yogurt is found again with the same Substitutes: recipe, ingredient, name \"yogurt\", amount 200, unit GRAM -- B2-S02 US-CA-02 INV-12 R3.E1 ADR0009 (answer as jsonb)")
  @Test
  void helpWithOneSubstituteRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, HelpRequests.gramsOf("yogurt", "200"));

    repository.save(request);

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found.status()).isEqualTo(HelpRequestStatus.ANSWERED);
              assertThat(found.helps())
                  .singleElement()
                  .extracting(Help::answer)
                  .isEqualTo(
                      new Substitutes(
                          HelpRequests.TOMATO_SOUP,
                          List.of(HelpRequests.gramsOf("yogurt", "200"))));
            });
  }

  @DisplayName(
      "a Help proposing \"100 g mascarpone + 100 g yogurt\" or 200 g quark is found again with both substitutes in order, the mix with both amounts -- B2-S02 US-CA-02 INV-12 R3.E3")
  @Test
  void severalSubstitutesAndAMixRoundTripInOrder() {
    Substitute mix =
        new Substitute(
            HelpRequests.CREME_FRAICHE,
            List.of(
                new SubstituteIngredient("mascarpone", new BigDecimal("100"), Unit.GRAM),
                new SubstituteIngredient("yogurt", new BigDecimal("100"), Unit.GRAM)));
    Substitute quark = HelpRequests.gramsOf("quark", "200");

    assertThat(storedAnswerOf(mix, quark))
        .isEqualTo(new Substitutes(HelpRequests.TOMATO_SOUP, List.of(mix, quark)));
  }

  @DisplayName(
      "a substitute amount of 0.125 kg is found again as exactly 0.125 -- B2-S02 US-CA-02 INV-12")
  @Test
  void anAmountIsStoredExactly() {
    BigDecimal amount = new BigDecimal("0.125");
    Substitute quark =
        new Substitute(
            HelpRequests.CREME_FRAICHE,
            List.of(new SubstituteIngredient("quark", amount, Unit.KILOGRAM)));

    assertThat(storedAnswerOf(quark))
        .isEqualTo(new Substitutes(HelpRequests.TOMATO_SOUP, List.of(quark)));
  }

  @DisplayName(
      "a substitute ingredient in each of the 13 units is found again in that unit -- B2-S02 US-CA-02 INV-12")
  @ParameterizedTest
  @EnumSource(Unit.class)
  void everyUnitRoundTrips(Unit unit) {
    Substitute quark =
        new Substitute(
            HelpRequests.CREME_FRAICHE,
            List.of(new SubstituteIngredient("quark", new BigDecimal("2"), unit)));

    assertThat(storedAnswerOf(quark))
        .isEqualTo(new Substitutes(HelpRequests.TOMATO_SOUP, List.of(quark)));
  }

  @DisplayName(
      "a stored preparation step explanation is still read back as such once answers can be of two kinds -- B3-S02 US-CA-03 (regression)")
  @Test
  void anExplanationStillRoundTripsBesideSubstitutes() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);

    repository.save(request);

    assertThat(answerOfTheStoredHelp()).isEqualTo(HelpRequests.BUTTER_WITH_THREE_PHOTOS);
  }

  @DisplayName(
      "saving after Grandma's substitutes appends one outbox row HelpProvided whose answer carries answerType INGREDIENT_SUBSTITUTE, recipe and substitute -- B2-S02 US-CA-02 INV-12 ADR0015 AsyncAPI HelpProvided")
  @Test
  void savingAppendsHelpProvidedWithTheSubstitutes() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, HelpRequests.gramsOf("yogurt", "200"));

    repository.save(request);

    JsonNode answer =
        new ObjectMapper()
            .readTree((String) outboxRow("HelpProvided").get("payload"))
            .get("answer");
    assertThat(answer.get("answerType").asText()).isEqualTo("INGREDIENT_SUBSTITUTE");
    assertThat(answer.get("recipe").asText())
        .isEqualTo(HelpRequests.TOMATO_SOUP.value().toString());
    assertThat(
            answer
                .get("substitute")
                .get(0)
                .get("substituteIngredients")
                .get(0)
                .get("name")
                .asText())
        .isEqualTo("yogurt");
  }

  // B3-S04, B3-S05, B3-S03, B3-S06 for ingredient requests

  @DisplayName(
      "an accepted Help of substitutes is found again accepted, the request CLOSED -- B3-S04 US-CA-02 INV-13")
  @Test
  void acceptedHelpOfSubstitutesRoundTrips() {
    HelpRequest request = answeredWithYogurt();

    request.accept(HelpRequests.HELP_ID, MONDAY_20_00);
    repository.save(request);

    HelpRequest found = stored();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(found.helps()).singleElement().extracting(Help::isAccepted).isEqualTo(true);
  }

  @DisplayName(
      "a rejected Help of substitutes is found again rejected, its note kept, each asked provider with the new deadline + 24 h -- B3-S05 US-CA-02 INV-14")
  @Test
  void rejectedHelpOfSubstitutesRoundTrips() {
    HelpRequest request = answeredWithYogurt();

    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_20_00);
    repository.save(request);

    HelpRequest found = stored();
    assertThat(found.helps()).singleElement().extracting(Help::isRejected).isEqualTo(true);
    assertThat(found.notes()).extracting(Note::text).containsExactly("too sour");
    assertThat(found.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsOnly(MONDAY_20_00.instant().plus(Duration.ofHours(24)));
  }

  @DisplayName(
      "after loading a request with a rejected yogurt Help, proposing yogurt again is rejected with SubstituteRejectedBefore -- B3-S05 US-CA-02 INV-15 (the guard works on stored state)")
  @Test
  void aRejectedSubstituteIsRefusedAfterLoading() {
    HelpRequest request = answeredWithYogurt();
    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_20_00);
    repository.save(request);

    HelpRequest found = stored();

    assertThatThrownBy(
            () -> HelpRequests.grandmaProposes(found, HelpRequests.gramsOf("Yogurt", "150")))
        .isInstanceOf(SubstituteRejectedBefore.class);
  }

  @DisplayName(
      "a cancelled ingredient request is found again CANCELLED with its note, ingredient and requirements -- B3-S03 US-CA-02 INV-16 R6.E1")
  @Test
  void cancelledIngredientRequestRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);

    request.cancel(Optional.of("found it at my neighbour's"), MONDAY_20_00);
    repository.save(request);

    HelpRequest found = stored();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(found.notes()).extracting(Note::text).containsExactly("found it at my neighbour's");
    assertThat(found.ingredient()).isEqualTo(HelpRequests.CREME_FRAICHE);
    assertThat(found.substituteRequirements()).isEqualTo("200 g, not available");
  }

  @DisplayName(
      "a timed-out ingredient request is found again CANCELLED with its providers TIMED_OUT -- B3-S06 US-CA-02 INV-17 R7.E1")
  @Test
  void timedOutIngredientRequestRoundTrips() {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY);

    request.timeOut(Clock.offset(HelpRequests.MONDAY_18_00, Duration.ofHours(24).plusSeconds(1)));
    repository.save(request);

    HelpRequest found = stored();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(found.askedProviders())
        .extracting(AskedProvider::state)
        .containsOnly(ProviderState.TIMED_OUT);
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

  /** Grandma proposes the substitutes; the answer as read back from the database. */
  private HelpAnswer storedAnswerOf(Substitute... substitutes) {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, substitutes);
    repository.save(request);
    return answerOfTheStoredHelp();
  }

  private HelpAnswer answerOfTheStoredHelp() {
    return repository
        .findById(HelpRequests.HELP_REQUEST_ID)
        .orElseThrow()
        .helps()
        .getFirst()
        .answer();
  }

  private static HelpRequest answeredWithYogurt() {
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, HelpRequests.gramsOf("yogurt", "200"));
    return request;
  }

  private HelpRequest stored() {
    return repository.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
  }

  @DisplayName(
      "the V8 migration adds the nullable column ingredient_name (text) to help_request -- B2-S01 US-CA-02 INV-11 ADR0009")
  @Test
  void v8AddsTheIngredientName() {
    List<String> columns =
        jdbc.sql(
                "select column_name || ' ' || data_type || ' ' || is_nullable"
                    + " from information_schema.columns"
                    + " where table_schema = 'cooking_assistance' and table_name = 'help_request'")
            .query(String.class)
            .list();

    assertThat(columns).contains("ingredient_name text YES");
  }

  @DisplayName(
      "an ingredient request is found again with its ingredient name \"creme fraiche\" -- B2-S01 US-CA-02 INV-11")
  @Test
  void theIngredientNameRoundTrips() {
    repository.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR));

    assertThat(repository.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(found -> assertThat(found.ingredientName()).isEqualTo("creme fraiche"));
  }
}
