package org.larder.cookingassistance.domain.helprequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.larder.cookingassistance.fixtures.HelpRequests;

/** Help with an ingredient substitute on the Help Request aggregate (US-CA-02). */
class HelpRequestIngredientTest {

  static final RecipeId TOMATO_SOUP =
      new RecipeId(UUID.fromString("3f0a6c1e-2b7d-4d9a-9c41-6a1f0e7b2c10"));
  static final IngredientId CREME_FRAICHE =
      new IngredientId(UUID.fromString("8956b2e5-8d0c-470e-ae9c-6071bd7c5b0d"));

  /** Monday 18:00 CET. */
  static final Clock MONDAY_18_00 =
      Clock.fixed(Instant.parse("2026-11-09T17:00:00Z"), ZoneOffset.UTC);

  /** Monday 19:00 CET. */
  static final Clock MONDAY_19_00 =
      Clock.fixed(Instant.parse("2026-11-09T18:00:00Z"), ZoneOffset.UTC);

  /** Monday 10:00 CET. */
  static final Clock MONDAY_10_00 =
      Clock.fixed(Instant.parse("2026-11-09T09:00:00Z"), ZoneOffset.UTC);

  /** One second past 24 h after Monday 18:00 CET. */
  static final Clock TUESDAY_18_00_01 =
      Clock.fixed(Instant.parse("2026-11-10T17:00:01Z"), ZoneOffset.UTC);

  static final Substitute YOGURT =
      new Substitute(
          CREME_FRAICHE,
          List.of(new SubstituteIngredient("yogurt", new BigDecimal("200"), Unit.GRAM)));

  static final Substitute QUARK = single("quark");
  static final Substitute SOUR_CREAM = single("sour cream");

  private static Substitute single(String name) {
    return new Substitute(
        CREME_FRAICHE, List.of(new SubstituteIngredient(name, new BigDecimal("200"), Unit.GRAM)));
  }

  private static void grandmaProposes(HelpRequest request, Substitute... substitutes) {
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's ideas",
        new Substitutes(TOMATO_SOUP, List.of(substitutes)),
        MONDAY_19_00);
  }

  private static HelpRequest requestFor(IngredientId ingredient) {
    return HelpRequest.requestHelpWithIngredient(
        HelpRequests.HELP_REQUEST_ID,
        HelpRequests.LENA,
        "A substitute",
        "The shop has none",
        TOMATO_SOUP,
        ingredient,
        "200 g, not available",
        Set.of(HelpProviderType.COMMUNITY),
        MONDAY_18_00);
  }

  private static HelpRequest lenasRequest(String requirements, Set<HelpProviderType> providers) {
    return HelpRequest.requestHelpWithIngredient(
        HelpRequests.HELP_REQUEST_ID,
        HelpRequests.LENA,
        "Creme fraiche in tomato soup",
        "The shop has none",
        TOMATO_SOUP,
        CREME_FRAICHE,
        requirements,
        providers,
        MONDAY_18_00);
  }

  // B2-S01: Request help ingredients

  @DisplayName(
      "Lena asks for a substitute for 200 g creme fraiche in tomato soup -> OPEN, type INGREDIENT_SUBSTITUTE, recipe, ingredient, requirements \"200 g, not available\", no step, version 0 -- B2-S01 US-CA-02 INV-11 R2.E1")
  @Test
  void requestIsOpenForOneIngredient() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.type()).isEqualTo(HelpType.INGREDIENT_SUBSTITUTE);
    assertThat(request.recipe()).isEqualTo(TOMATO_SOUP);
    assertThat(request.ingredient()).isEqualTo(CREME_FRAICHE);
    assertThat(request.substituteRequirements()).isEqualTo("200 g, not available");
    assertThat(request.howToStep()).isNull();
    assertThat(request.version()).isZero();
  }

  @DisplayName(
      "the request raises one HelpRequested with ingredient, substituteRequirements, askedProviders, status OPEN and occurredAt, and no howToStep -- B2-S01 US-CA-02 INV-11 AsyncAPI HelpRequestCreated")
  @Test
  void requestRaisesHelpRequestedWithTheIngredient() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> {
              assertThat(requested.type()).isEqualTo(HelpType.INGREDIENT_SUBSTITUTE);
              assertThat(requested.recipe()).isEqualTo(TOMATO_SOUP);
              assertThat(requested.ingredient()).isEqualTo(CREME_FRAICHE);
              assertThat(requested.substituteRequirements()).isEqualTo("200 g, not available");
              assertThat(requested.howToStep()).isNull();
              assertThat(requested.status()).isEqualTo(HelpRequestStatus.OPEN);
              assertThat(requested.occurredAt()).isEqualTo(MONDAY_18_00.instant());
            });
  }

  @DisplayName(
      "Lena asks Grandma -> Grandma is asked, deadline requested + 24 h, on the request and its event -- B2-S01 US-CA-02 INV-10 INV-17 R1.E1")
  @Test
  void grandmaIsAskedWithA24HourDeadline() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));

    AskedProvider grandma =
        new AskedProvider(
            HelpProviderType.GRANDMA_AVATAR, MONDAY_18_00.instant().plus(Duration.ofHours(24)));
    assertThat(request.askedProviders()).containsExactly(grandma);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> assertThat(requested.askedProviders()).containsExactly(grandma));
  }

  @DisplayName(
      "Lena asks the Community and Grandma -> both asked, each with deadline + 24 h -- B2-S01 US-CA-02 INV-10 R1.E2")
  @Test
  void bothProvidersGetTheSameRequest() {
    HelpRequest request =
        lenasRequest(
            "200 g, not available",
            Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));

    var deadline = MONDAY_18_00.instant().plus(Duration.ofHours(24));
    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, deadline),
            new AskedProvider(HelpProviderType.GRANDMA_AVATAR, deadline));
  }

  @DisplayName(
      "Tom (free plan) asks the Community -> request OPEN, no plan guard -- B2-S01 US-CA-02 INV-10 R1.E3")
  @Test
  void everyCookCanAsk() {
    CookId tom = new CookId(UUID.fromString("b1f2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d"));

    HelpRequest request =
        HelpRequest.requestHelpWithIngredient(
            HelpRequests.HELP_REQUEST_ID,
            tom,
            "Creme fraiche in tomato soup",
            "The shop has none",
            TOMATO_SOUP,
            CREME_FRAICHE,
            "200 g, not available",
            Set.of(HelpProviderType.COMMUNITY),
            MONDAY_18_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.requester()).isEqualTo(tom);
  }

  @DisplayName(
      "Chef among the preferred providers -> rejected with ChefNotAllowed, rule INV-01 -- B2-S01 US-CA-02 INV-10 R1.E4")
  @Test
  void chefIsNeverAsked() {
    assertThatThrownBy(() -> lenasRequest("200 g, not available", Set.of(HelpProviderType.CHEF)))
        .isInstanceOfSatisfying(
            ChefNotAllowed.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-01"));
  }

  @DisplayName(
      "no provider chosen -> rejected with NoHelpProviderChosen, rule INV-01 -- B2-S01 US-CA-02 INV-10")
  @Test
  void someProviderMustBeChosen() {
    assertThatThrownBy(() -> lenasRequest("200 g, not available", Set.of()))
        .isInstanceOfSatisfying(
            NoHelpProviderChosen.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-01"));
  }

  @DisplayName("no recipe -> rejected with RecipeRequired, rule INV-02 -- B2-S01 US-CA-02 INV-11")
  @Test
  void recipeIsRequired() {
    assertThatThrownBy(
            () ->
                HelpRequest.requestHelpWithIngredient(
                    HelpRequests.HELP_REQUEST_ID,
                    HelpRequests.LENA,
                    "Creme fraiche in tomato soup",
                    "The shop has none",
                    null,
                    CREME_FRAICHE,
                    "200 g, not available",
                    Set.of(HelpProviderType.GRANDMA_AVATAR),
                    MONDAY_18_00))
        .isInstanceOfSatisfying(
            RecipeRequired.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-02"));
  }

  @DisplayName(
      "no ingredient -> rejected with IngredientRequired, rule INV-11 -- B2-S01 US-CA-02 INV-11")
  @Test
  void ingredientIsRequired() {
    assertThatThrownBy(
            () ->
                HelpRequest.requestHelpWithIngredient(
                    HelpRequests.HELP_REQUEST_ID,
                    HelpRequests.LENA,
                    "Creme fraiche in tomato soup",
                    "The shop has none",
                    TOMATO_SOUP,
                    null,
                    "200 g, not available",
                    Set.of(HelpProviderType.GRANDMA_AVATAR),
                    MONDAY_18_00))
        .isInstanceOfSatisfying(
            IngredientRequired.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-11"));
  }

  @DisplayName(
      "no or blank substitute requirements -> rejected with SubstituteRequirementsRequired, rule INV-11 -- B2-S01 US-CA-02 INV-11")
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"   "})
  void substituteRequirementsAreRequired(String requirements) {
    assertThatThrownBy(() -> lenasRequest(requirements, Set.of(HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOfSatisfying(
            SubstituteRequirementsRequired.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-11"));
  }

  @DisplayName(
      "bacon in carbonara for a vegetarian guest -> requirements show \"vegetarian\" -- B2-S01 US-CA-02 INV-11 R2.E2")
  @Test
  void requirementsAreKeptAsWritten() {
    HelpRequest request = lenasRequest("vegetarian", Set.of(HelpProviderType.COMMUNITY));

    assertThat(request.substituteRequirements()).isEqualTo("vegetarian");
  }

  @DisplayName(
      "creme fraiche and bacon -> two requests, each with its one ingredient -- B2-S01 US-CA-02 INV-11 R2.E3")
  @Test
  void twoIngredientsAreTwoRequests() {
    IngredientId bacon = new IngredientId(UUID.fromString("c2d3e4f5-6a7b-4c8d-9e0f-1a2b3c4d5e6f"));

    HelpRequest forCremeFraiche = requestFor(CREME_FRAICHE);
    HelpRequest forBacon = requestFor(bacon);

    assertThat(forCremeFraiche.ingredient()).isEqualTo(CREME_FRAICHE);
    assertThat(forBacon.ingredient()).isEqualTo(bacon);
  }

  // B2-S02: Provide Help

  @DisplayName(
      "Jonas proposes yogurt -> Help with its substitutes, request ANSWERED -- B2-S02 US-CA-02 INV-12")
  @Test
  void anAnswerMakesTheRequestAnswered() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Use yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);

    assertThat(request.helps()).hasSize(1);
    assertThat(request.helps().get(0).answer())
        .isEqualTo(new Substitutes(TOMATO_SOUP, List.of(YOGURT)));
    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "the answer raises one HelpProvided carrying the Substitutes answer, provider type and person, status ANSWERED -- B2-S02 US-CA-02 INV-12 AsyncAPI HelpProvided")
  @Test
  void anAnswerRaisesHelpProvidedWithSubstitutes() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    request.pullEvents();

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Use yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);

    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpProvided.class,
            provided -> {
              assertThat(provided.answer())
                  .isEqualTo(new Substitutes(TOMATO_SOUP, List.of(YOGURT)));
              assertThat(provided.helpProviderType()).isEqualTo(HelpProviderType.COMMUNITY);
              assertThat(provided.helpProvider()).contains(HelpRequests.JONAS);
              assertThat(provided.helpRequestStatus()).isEqualTo(HelpRequestStatus.ANSWERED);
              assertThat(provided.occurredAt()).isEqualTo(MONDAY_19_00.instant());
            });
  }

  @DisplayName(
      "Grandma proposes yogurt, quark and sour cream -> three substitutes on one Help -- B2-S02 US-CA-02 INV-12 R3.E1")
  @Test
  void anAnswerCanProposeSeveralSubstitutes() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    Substitutes three = new Substitutes(TOMATO_SOUP, List.of(YOGURT, QUARK, SOUR_CREAM));

    request.provideHelp(
        HelpRequests.HELP_ID, HelpProvider.grandmaAvatar(), "Three ideas", three, MONDAY_19_00);

    assertThat(request.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.answer()).isEqualTo(three));
  }

  @DisplayName(
      "Grandma proposes 100 g mascarpone + 100 g yogurt -> one substitute of two ingredients with name, value and unit -- B2-S02 US-CA-02 INV-12 R3.E3")
  @Test
  void aSubstituteCanBeAMixWithAmounts() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    Substitute mix =
        new Substitute(
            CREME_FRAICHE,
            List.of(
                new SubstituteIngredient("mascarpone", new BigDecimal("100"), Unit.GRAM),
                new SubstituteIngredient("yogurt", new BigDecimal("100"), Unit.GRAM)));
    Substitutes answer = new Substitutes(TOMATO_SOUP, List.of(mix));

    request.provideHelp(
        HelpRequests.HELP_ID, HelpProvider.grandmaAvatar(), "A mix", answer, MONDAY_19_00);

    assertThat(request.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.answer()).isEqualTo(answer));
  }

  @DisplayName(
      "Jonas proposes yogurt, Mia mascarpone -> two Helps, request ANSWERED -- B2-S02 US-CA-02 INV-12 R3.E2")
  @Test
  void severalProvidersCanAnswer() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.community(HelpRequests.MIA),
        "Quark",
        new Substitutes(TOMATO_SOUP, List.of(QUARK)),
        MONDAY_19_00);

    assertThat(request.helps()).hasSize(2);
    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "no substitute -> rejected with NoSubstituteProposed, rule INV-12, no Help, no event -- B2-S02 US-CA-02 INV-12")
  @Test
  void anAnswerProposesAtLeastOneSubstitute() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    request.pullEvents();

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Nothing",
                    new Substitutes(TOMATO_SOUP, List.of()),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            NoSubstituteProposed.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-12"));
    assertThat(request.helps()).isEmpty();
    assertThat(request.pullEvents()).isEmpty();
  }

  @DisplayName(
      "a substitute without ingredients -> rejected with NoSubstituteProposed, rule INV-12 -- B2-S02 US-CA-02 INV-12")
  @Test
  void aSubstituteNamesAtLeastOneIngredient() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Nothing",
                    new Substitutes(
                        TOMATO_SOUP, List.of(YOGURT, new Substitute(CREME_FRAICHE, List.of()))),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            NoSubstituteProposed.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-12"));
  }

  @DisplayName(
      "a substitute ingredient with a blank name -> rejected with NoSubstituteProposed, rule INV-12, no Help -- B2-S02 US-CA-02 INV-12 (step 3 r13)")
  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void aSubstituteIngredientHasAName(String blank) {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Nothing",
                    new Substitutes(TOMATO_SOUP, List.of(single(blank))),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            NoSubstituteProposed.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-12"));
    assertThat(request.helps()).isEmpty();
  }

  @DisplayName(
      "a mix of \"yogurt\" and a blank name -> rejected with NoSubstituteProposed, rule INV-12 -- B2-S02 US-CA-02 INV-12 (step 3 r13)")
  @Test
  void everyIngredientOfAMixHasAName() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Nothing",
                    new Substitutes(TOMATO_SOUP, List.of(mixOf("yogurt", "   "))),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            NoSubstituteProposed.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-12"));
    assertThat(request.helps()).isEmpty();
  }

  @DisplayName(
      "a preparation step explanation on an ingredient request -> rejected with AnswerDoesNotMatchRequest, rule INV-37 -- B2-S02 US-CA-02 INV-37")
  @Test
  void anExplanationDoesNotAnswerAnIngredientRequest() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Warm the bowl",
                    new PreparationStepExplanation(
                        TOMATO_SOUP, HelpRequests.STEP_4, "Warm the bowl", List.of()),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
  }

  @DisplayName(
      "substitutes on a preparation step request -> rejected with AnswerDoesNotMatchRequest, rule INV-37 -- B2-S02 US-CA-02 INV-37")
  @Test
  void substitutesDoNotAnswerAStepRequest() {
    HelpRequest request =
        HelpRequest.requestHelpWithPreparationStep(
            HelpRequests.HELP_REQUEST_ID,
            HelpRequests.LENA,
            "Sauce splits",
            "It splits at step 4",
            TOMATO_SOUP,
            HelpRequests.STEP_4,
            Set.of(HelpProviderType.COMMUNITY),
            MONDAY_18_00);

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Yogurt",
                    new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
  }

  @DisplayName(
      "substitutes for another recipe -> rejected with AnswerDoesNotMatchRequest, rule INV-37 -- B2-S02 US-CA-02 INV-37")
  @Test
  void substitutesAreForTheRequestsRecipe() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Yogurt",
                    new Substitutes(HelpRequests.HOLLANDAISE, List.of(YOGURT)),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
  }

  @DisplayName(
      "one of two substitutes names another ingredient -> rejected with AnswerDoesNotMatchRequest, rule INV-37 -- B2-S02 US-CA-02 INV-37")
  @Test
  void everySubstituteIsForTheRequestsIngredient() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    IngredientId bacon = new IngredientId(UUID.fromString("c2d3e4f5-6a7b-4c8d-9e0f-1a2b3c4d5e6f"));
    Substitute forBacon = new Substitute(bacon, YOGURT.substituteIngredients());

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Yogurt",
                    new Substitutes(TOMATO_SOUP, List.of(YOGURT, forBacon)),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
  }

  @DisplayName(
      "Lena answers her own request -> rejected with OwnRequest, rule INV-36 -- B2-S02 US-CA-02 INV-36")
  @Test
  void aCookNeverAnswersTheirOwnRequest() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.LENA),
                    "Yogurt",
                    new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            OwnRequest.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-36"));
  }

  @DisplayName(
      "Community answers when only Grandma was asked -> rejected with ProviderNotAsked, rule INV-01 -- B2-S02 US-CA-02 INV-10")
  @Test
  void onlyAnAskedProviderAnswers() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Yogurt",
                    new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            ProviderNotAsked.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-01"));
  }

  @DisplayName(
      "Jonas answers Tue 17:59, asked Mon 18:00 -> answer counts -- B2-S02 US-CA-02 INV-17 R7.E4")
  @Test
  void anAnswerAtOneMinuteBeforeTheDeadlineCounts() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    Clock answerTime =
        Clock.fixed(
            MONDAY_18_00.instant().plus(Duration.ofHours(24)).minusSeconds(60), ZoneOffset.UTC);

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        answerTime);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName("an answer exactly 24 h after the request -> counts -- B2-S02 US-CA-02 INV-17")
  @Test
  void anAnswerAtTheDeadlineCounts() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    Clock answerTime =
        Clock.fixed(MONDAY_18_00.instant().plus(Duration.ofHours(24)), ZoneOffset.UTC);

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        answerTime);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "an answer one second after 24 h -> rejected with AnswerTooLate, rule INV-09 -- B2-S02 US-CA-02 INV-17")
  @Test
  void anAnswerAfterTheDeadlineIsTooLate() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    Clock oneSecondLate =
        Clock.fixed(
            MONDAY_18_00.instant().plus(Duration.ofHours(24)).plusSeconds(1), ZoneOffset.UTC);

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Yogurt",
                    new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
                    oneSecondLate))
        .isInstanceOfSatisfying(
            AnswerTooLate.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-09"));
  }

  @DisplayName(
      "an answer on a CLOSED or CANCELLED request -> rejected with InvalidHelpRequestState -- B2-S02 US-CA-02 INV-12")
  @Test
  void aClosedOrCancelledRequestTakesNoAnswer() {
    HelpRequest closed = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    closed.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);
    closed.accept(HelpRequests.HELP_ID, MONDAY_19_00);
    HelpRequest cancelled =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    cancelled.cancel(Optional.empty(), MONDAY_19_00);

    assertThatThrownBy(() -> miaProposesQuarkOn(closed))
        .as("closed")
        .isInstanceOf(InvalidHelpRequestState.class);
    assertThatThrownBy(() -> miaProposesQuarkOn(cancelled))
        .as("cancelled")
        .isInstanceOf(InvalidHelpRequestState.class);
  }

  private static void miaProposesQuarkOn(HelpRequest request) {
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.community(HelpRequests.MIA),
        "Quark",
        new Substitutes(TOMATO_SOUP, List.of(QUARK)),
        MONDAY_19_00);
  }

  // B3-S04 for ingredients: Accept help

  @DisplayName(
      "Grandma proposes three, Lena accepts that Help -> CLOSED, Help accepted, HelpAccepted raised -- B3-S04 US-CA-02 INV-13 R3.E1")
  @Test
  void acceptingAHelpClosesTheRequest() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, YOGURT, QUARK, SOUR_CREAM);
    request.pullEvents();

    request.accept(HelpRequests.HELP_ID, MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(request.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.isAccepted()).isTrue());
    assertThat(request.pullEvents()).singleElement().isInstanceOf(HelpAccepted.class);
  }

  @DisplayName(
      "Jonas and Mia answered, Lena accepts Jonas -> CLOSED, only that Help accepted -- B3-S04 US-CA-02 INV-13 R3.E2")
  @Test
  void acceptingOneOfTwoHelpsClosesTheRequest() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);
    miaProposesQuarkOn(request);

    request.accept(HelpRequests.HELP_ID, MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(request.helps().stream().filter(Help::isAccepted).map(Help::id))
        .containsExactly(HelpRequests.HELP_ID);
  }

  @DisplayName(
      "Lena accepts the mix -> CLOSED, the mix with its amounts stays on the accepted Help -- B3-S04 US-CA-02 INV-13 R3.E3")
  @Test
  void acceptingAMixClosesTheRequest() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    Substitute mix =
        new Substitute(
            CREME_FRAICHE,
            List.of(
                new SubstituteIngredient("mascarpone", new BigDecimal("100"), Unit.GRAM),
                new SubstituteIngredient("yogurt", new BigDecimal("100"), Unit.GRAM)));
    grandmaProposes(request, mix);

    request.accept(HelpRequests.HELP_ID, MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(request.helps())
        .singleElement()
        .satisfies(
            help ->
                assertThat(help.answer()).isEqualTo(new Substitutes(TOMATO_SOUP, List.of(mix))));
  }

  @DisplayName(
      "after the acceptance -> stays CLOSED, no further event: what she cooks is her choice -- B3-S04 US-CA-02 INV-13 R3.E4")
  @Test
  void nothingFollowsAnAcceptance() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, YOGURT);
    request.accept(HelpRequests.HELP_ID, MONDAY_19_00);
    request.pullEvents();

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(request.pullEvents()).isEmpty();
  }

  @DisplayName(
      "both asked, Lena accepts Grandma -> CLOSED; a later Community answer is rejected with InvalidHelpRequestState -- B3-S04 US-CA-02 INV-13 R3.E5")
  @Test
  void acceptingClosesForTheOtherProviderToo() {
    HelpRequest request =
        lenasRequest(
            "200 g, not available",
            Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, QUARK);

    request.accept(HelpRequests.HELP_ID, MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThatThrownBy(() -> miaProposesQuarkOn(request))
        .isInstanceOf(InvalidHelpRequestState.class);
  }

  // B3-S05 for ingredients: Reject help

  @DisplayName(
      "Lena rejects Grandma -> OPEN, Grandma asked again, deadline the rejection + 24 h (map: Mon 11:00 -> Tue 11:00) -- B3-S05 US-CA-02 INV-14 R4.E1")
  @Test
  void aRejectionSendsItAgainWithA24HourDeadline() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, YOGURT);

    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR,
                MONDAY_19_00.instant().plus(Duration.ofHours(24))));
  }

  @DisplayName(
      "Jonas answers there is no good substitute for saffron, rejected -> OPEN -- B3-S05 US-CA-02 INV-14 R4.E2")
  @Test
  void noGoodSubstituteStaysOpen() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "No good substitute for saffron",
        new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
        MONDAY_19_00);

    request.reject(HelpRequests.HELP_ID, "no good substitute", MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
  }

  @DisplayName(
      "both asked, nothing accepted, one rejected -> both asked again, each deadline + 24 h -- B3-S05 US-CA-02 INV-14 R4.E3")
  @Test
  void aRejectionSendsItAgainToBoth() {
    HelpRequest request =
        lenasRequest(
            "200 g, not available",
            Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, YOGURT);

    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_19_00);

    var deadline = MONDAY_19_00.instant().plus(Duration.ofHours(24));
    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, deadline),
            new AskedProvider(HelpProviderType.GRANDMA_AVATAR, deadline));
  }

  @DisplayName(
      "Lena rejects mascarpone, too heavy -> rejection note kept naming that Help -- B3-S05 US-CA-02 INV-15 R5.E1")
  @Test
  void aRejectionKeepsItsNote() {
    HelpRequest request =
        lenasRequest("200 g, not available", Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, single("mascarpone"));

    request.reject(HelpRequests.HELP_ID, "mascarpone - too heavy", MONDAY_19_00);

    assertThat(request.notes())
        .singleElement()
        .satisfies(
            note -> {
              assertThat(note.text()).isEqualTo("mascarpone - too heavy");
              assertThat(note.help()).contains(HelpRequests.HELP_ID);
            });
  }

  @DisplayName(
      "mascarpone rejected, sent again, Jonas proposes mascarpone -> rejected with SubstituteRejectedBefore, rule INV-15, no Help, no event -- B2-S02 US-CA-02 INV-15 R5.E2")
  @Test
  void aRejectedSubstituteIsNotProposedAgain() {
    HelpRequest request =
        lenasRequest(
            "200 g, not available",
            Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    grandmaProposes(request, single("mascarpone"));
    request.reject(HelpRequests.HELP_ID, "mascarpone - too heavy", MONDAY_19_00);
    request.pullEvents();

    assertThatThrownBy(() -> jonasProposes(request, single("mascarpone")))
        .isInstanceOfSatisfying(
            SubstituteRejectedBefore.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-15"));
    assertThat(request.helps()).hasSize(1);
    assertThat(request.pullEvents()).isEmpty();
  }

  private static HelpRequest bothAsked() {
    return lenasRequest(
        "200 g, not available",
        Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
  }

  private static Substitute amountOf(String name, String grams) {
    return new Substitute(
        CREME_FRAICHE, List.of(new SubstituteIngredient(name, new BigDecimal(grams), Unit.GRAM)));
  }

  private static Substitute mixOf(String first, String second) {
    return new Substitute(
        CREME_FRAICHE,
        List.of(
            new SubstituteIngredient(first, new BigDecimal("100"), Unit.GRAM),
            new SubstituteIngredient(second, new BigDecimal("100"), Unit.GRAM)));
  }

  private static void jonasProposes(HelpRequest request, Substitute... substitutes) {
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Jonas's idea",
        new Substitutes(TOMATO_SOUP, List.of(substitutes)),
        MONDAY_19_00);
  }

  @DisplayName(
      "mascarpone rejected at 200 g, proposed at 100 g -> SubstituteRejectedBefore -- B2-S02 US-CA-02 INV-15")
  @Test
  void anotherAmountIsTheSameSubstitute() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, single("mascarpone"));
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    assertThatThrownBy(() -> jonasProposes(request, amountOf("mascarpone", "100")))
        .isInstanceOf(SubstituteRejectedBefore.class);
  }

  @DisplayName(
      "\" Mascarpone\" after \"mascarpone\" -> SubstituteRejectedBefore -- B2-S02 US-CA-02 INV-15")
  @Test
  void namesAreComparedWithoutCaseOrOuterSpace() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, single("mascarpone"));
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    assertThatThrownBy(() -> jonasProposes(request, single(" Mascarpone")))
        .isInstanceOf(SubstituteRejectedBefore.class);
  }

  @DisplayName(
      "the mix mascarpone + yogurt rejected, yogurt + mascarpone proposed -> SubstituteRejectedBefore -- B2-S02 US-CA-02 INV-15")
  @Test
  void theSameMixIsTheSameSubstitute() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, mixOf("mascarpone", "yogurt"));
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    assertThatThrownBy(() -> jonasProposes(request, mixOf("yogurt", "mascarpone")))
        .isInstanceOf(SubstituteRejectedBefore.class);
  }

  @DisplayName(
      "mascarpone rejected, the mix mascarpone + yogurt proposed -> allowed -- B2-S02 US-CA-02 INV-15")
  @Test
  void aMixWithARejectedIngredientIsANewSubstitute() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, single("mascarpone"));
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    jonasProposes(request, mixOf("mascarpone", "yogurt"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "a Help with yogurt and quark rejected, quark proposed -> SubstituteRejectedBefore -- B2-S02 US-CA-02 INV-15")
  @Test
  void everySubstituteOfARejectedHelpIsRejected() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, YOGURT, QUARK);
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    assertThatThrownBy(() -> jonasProposes(request, QUARK))
        .isInstanceOf(SubstituteRejectedBefore.class);
  }

  @DisplayName("mascarpone rejected, Jonas proposes quark -> ANSWERED -- B2-S02 US-CA-02 INV-15")
  @Test
  void anotherSubstituteIsWelcome() {
    HelpRequest request = bothAsked();
    grandmaProposes(request, single("mascarpone"));
    request.reject(HelpRequests.HELP_ID, "too heavy", MONDAY_19_00);

    jonasProposes(request, QUARK);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  // B3-S03 for ingredients: Cancel help request

  @DisplayName(
      "Lena cancels: \"found it at my neighbour's\" -> CANCELLED, note kept, event carries it -- B3-S03 US-CA-02 INV-16 R6.E1")
  @Test
  void cancellingKeepsTheNote() {
    HelpRequest request = bothAsked();
    request.pullEvents();

    request.cancel(Optional.of("found it at my neighbour's"), MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.notes())
        .containsExactly(Note.cancellation("found it at my neighbour's", MONDAY_19_00.instant()));
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class,
            cancelled -> assertThat(cancelled.note()).contains("found it at my neighbour's"));
  }

  @DisplayName("Lena cancels without a note -> CANCELLED -- B3-S03 US-CA-02 INV-16 R6.E2")
  @Test
  void cancellingWithoutANote() {
    HelpRequest request = bothAsked();
    request.pullEvents();

    request.cancel(Optional.empty(), MONDAY_19_00);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class, cancelled -> assertThat(cancelled.note()).isEmpty());
  }

  // B3-S06 for ingredients: Time out help request

  private static void assertTimedOutSuggesting(
      HelpRequest request, HelpProviderType... suggestedProviders) {
    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestTimedOut.class,
            timedOut ->
                assertThat(timedOut.suggestedProviders()).containsExactly(suggestedProviders));
  }

  @DisplayName(
      "only Community asked Mon 18:00, silent until Tue 18:00 and after -> CANCELLED, Grandma suggested -- B3-S06 US-CA-02 INV-17 R7.E1")
  @Test
  void silentCommunitySuggestsGrandma() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    request.pullEvents();

    request.timeOut(TUESDAY_18_00_01);

    assertTimedOutSuggesting(request, HelpProviderType.GRANDMA_AVATAR);
  }

  @DisplayName(
      "only Grandma asked Mon 10:00, silent 24 h -> CANCELLED, Community suggested -- B3-S06 US-CA-02 INV-17 R7.E2")
  @Test
  void silentGrandmaSuggestsCommunity() {
    HelpRequest request =
        HelpRequest.requestHelpWithIngredient(
            HelpRequests.HELP_REQUEST_ID,
            HelpRequests.LENA,
            "Creme fraiche in tomato soup",
            "The shop has none",
            TOMATO_SOUP,
            CREME_FRAICHE,
            "200 g, not available",
            Set.of(HelpProviderType.GRANDMA_AVATAR),
            MONDAY_10_00);
    request.pullEvents();

    request.timeOut(Clock.offset(MONDAY_10_00, Duration.ofHours(24).plusSeconds(1)));

    assertTimedOutSuggesting(request, HelpProviderType.COMMUNITY);
  }

  @DisplayName(
      "both asked, both silent 24 h -> CANCELLED, no suggestion: Lena is only told -- B3-S06 US-CA-02 INV-17 R7.E3 Q1")
  @Test
  void bothSilentSuggestNothing() {
    HelpRequest request = bothAsked();
    request.pullEvents();

    request.timeOut(TUESDAY_18_00_01);

    assertTimedOutSuggesting(request);
  }

  @DisplayName(
      "time-out one second before 24 h -> rejected with DeadlineNotPassed, rule INV-09 -- B3-S06 US-CA-02 INV-17")
  @Test
  void noTimeOutBeforeTheDeadline() {
    HelpRequest request = bothAsked();
    request.pullEvents();

    assertThatThrownBy(
            () -> request.timeOut(Clock.offset(MONDAY_18_00, Duration.ofHours(24).minusSeconds(1))))
        .isInstanceOfSatisfying(
            DeadlineNotPassed.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-09"));
    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.pullEvents()).isEmpty();
  }

  // Reconstitution

  @DisplayName(
      "a stored ingredient request with a Help of substitutes -> same state, no events, no guards -- B2-S01,B2-S02 US-CA-02")
  @Test
  void reconstitutionRebuildsAnIngredientRequestWithoutEvents() {
    Help stored =
        Help.reconstitute(
            HelpRequests.HELP_ID,
            HelpProvider.grandmaAvatar(),
            "Grandma's ideas",
            new Substitutes(TOMATO_SOUP, List.of(YOGURT)),
            false);

    // A Chef is refused when asking; stored state is taken as it is
    HelpRequest request =
        HelpRequest.reconstitute(
            HelpRequests.HELP_REQUEST_ID,
            HelpRequests.LENA,
            "Creme fraiche in tomato soup",
            "The shop has none",
            HelpType.INGREDIENT_SUBSTITUTE,
            TOMATO_SOUP,
            null,
            CREME_FRAICHE,
            "200 g, not available",
            List.of(new AskedProvider(HelpProviderType.CHEF, MONDAY_18_00.instant())),
            HelpRequestStatus.ANSWERED,
            List.of(stored),
            List.of(),
            3L);

    assertThat(request.ingredient()).isEqualTo(CREME_FRAICHE);
    assertThat(request.substituteRequirements()).isEqualTo("200 g, not available");
    assertThat(request.type()).isEqualTo(HelpType.INGREDIENT_SUBSTITUTE);
    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(request.helps()).containsExactly(stored);
    assertThat(request.version()).isEqualTo(3L);
    assertThat(request.pullEvents()).isEmpty();
  }

  @DisplayName(
      "substitutes handed over as a HelpAnswer -> recorded like any substitutes, request ANSWERED -- B2-S02 US-CA-02 INV-12 (step 6 r1)")
  @Test
  void substitutesGivenAsAnAnswerAreRecorded() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    HelpAnswer answer = new Substitutes(TOMATO_SOUP, List.of(YOGURT));

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Yogurt",
        answer,
        MONDAY_19_00);

    assertThat(request.helps()).singleElement().extracting(Help::answer).isEqualTo(answer);
    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "a preparation step explanation handed over as a HelpAnswer on an ingredient request -> rejected with AnswerDoesNotMatchRequest, rule INV-37 -- B2-S02 US-CA-02 INV-37 (step 6 r1)")
  @Test
  void anExplanationGivenAsAnAnswerIsRefused() {
    HelpRequest request = lenasRequest("200 g, not available", Set.of(HelpProviderType.COMMUNITY));
    HelpAnswer answer = HelpRequests.BUTTER_WITH_THREE_PHOTOS;

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.community(HelpRequests.JONAS),
                    "Butter",
                    answer,
                    MONDAY_19_00))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
    assertThat(request.helps()).isEmpty();
  }
}
