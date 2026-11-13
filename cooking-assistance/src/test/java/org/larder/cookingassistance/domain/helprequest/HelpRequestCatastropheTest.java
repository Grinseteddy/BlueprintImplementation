package org.larder.cookingassistance.domain.helprequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.fixtures.HelpRequests;

// Board B4, US-CA-04 (increment 3): the Help Request aggregate for a catastrophe; test list
// approved 2026-11-12
class HelpRequestCatastropheTest {

  /** Saturday 14 November 2026, 18:40 in Berlin (CET). */
  static final Clock SATURDAY_18_40 =
      Clock.fixed(Instant.parse("2026-11-14T17:40:00Z"), ZoneOffset.UTC);

  static final Clock SATURDAY_18_42 =
      Clock.fixed(Instant.parse("2026-11-14T17:42:00Z"), ZoneOffset.UTC);

  static final Instant SATURDAY_18_45 = Instant.parse("2026-11-14T17:45:00Z");

  static final Instant SATURDAY_18_55 = Instant.parse("2026-11-14T17:55:00Z");

  static final RecipeId ROAST_CHICKEN =
      new RecipeId(UUID.fromString("5c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f"));

  private static HelpRequest lenaAsks(Set<HelpProviderType> providers) {
    return lenaAsks(providers, Optional.empty());
  }

  static final URI BURNT_BOTTOM = URI.create("https://photos.example/lena/burnt-bottom.jpg");

  private static HelpRequest lenaAsks(Set<HelpProviderType> providers, Optional<RecipeId> recipe) {
    return lenaAsks(providers, recipe, List.of());
  }

  private static HelpRequest lenaAsks(
      Set<HelpProviderType> providers, Optional<RecipeId> recipe, List<URI> photos) {
    return HelpRequest.requestHelpWithCatastrophe(
        HelpRequests.HELP_REQUEST_ID,
        HelpRequests.LENA,
        "Roast chicken burnt",
        "Roast chicken burnt on the bottom, guests at 19:00",
        recipe,
        photos,
        providers,
        SATURDAY_18_40);
  }

  @DisplayName(
      "Sat 18:40 Lena asks Grandma about her burnt roast chicken -> OPEN, Grandma asked until 18:45, HelpRequested of type STEPS_TO_MITIGATE_CATASTROPHE -- B4-S01 US-CA-04 INV-18 INV-26 R1.E1")
  @Test
  void lenaAsksGrandmaAboutHerBurntRoastChicken() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.type()).isEqualTo(HelpType.STEPS_TO_MITIGATE_CATASTROPHE);
    assertThat(request.askedProviders())
        .containsExactly(new AskedProvider(HelpProviderType.GRANDMA_AVATAR, SATURDAY_18_45));
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> {
              assertThat(requested.type()).isEqualTo(HelpType.STEPS_TO_MITIGATE_CATASTROPHE);
              assertThat(requested.askedProviders()).isEqualTo(request.askedProviders());
              assertThat(requested.occurredAt()).isEqualTo(SATURDAY_18_40.instant());
            });
  }

  @DisplayName(
      "Lena asks the Community and Grandma together -> one request, Community until 18:55, Grandma until 18:45 -- B4-S01 US-CA-04 INV-18 INV-26 R1.E2")
  @Test
  void lenaAsksTheCommunityAndGrandmaTogether() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, SATURDAY_18_55),
            new AskedProvider(HelpProviderType.GRANDMA_AVATAR, SATURDAY_18_45));
    assertThat(request.pullEvents()).hasSize(1);
  }

  @DisplayName(
      "Tom (free plan) asks about his collapsed souffle -> request OPEN, no plan guard -- B4-S01 US-CA-04 INV-18 R1.E3")
  @Test
  void tomOnTheFreePlanAsksAboutHisCollapsedSouffle() {
    CookId tom = new CookId(UUID.fromString("b1f2c3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d"));

    HelpRequest request =
        HelpRequest.requestHelpWithCatastrophe(
            HelpRequests.HELP_REQUEST_ID,
            tom,
            "Collapsed souffle",
            "My cheese souffle collapsed as I took it out",
            Optional.empty(),
            List.of(),
            Set.of(HelpProviderType.COMMUNITY),
            SATURDAY_18_40);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.requester()).isEqualTo(tom);
  }

  @DisplayName(
      "Lena asks chef Marco about her burnt roast chicken -> rejected with ChefNotAllowed, rule INV-01 (INV-18 = INV-01), no request -- B4-S01 US-CA-04 INV-18 R1.E4 (step 3 r20)")
  @Test
  void aChefIsNeverAskedAboutACatastrophe() {
    assertThatThrownBy(() -> lenaAsks(Set.of(HelpProviderType.CHEF)))
        .isInstanceOfSatisfying(
            ChefNotAllowed.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-01"));
  }

  @DisplayName(
      "no provider chosen -> rejected with NoHelpProviderChosen, rule INV-01 -- B4-S01 US-CA-04 INV-18")
  @Test
  void aCatastropheRequestAsksAtLeastOneProvider() {
    assertThatThrownBy(() -> lenaAsks(Set.of()))
        .isInstanceOfSatisfying(
            NoHelpProviderChosen.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-01"));
  }

  @DisplayName(
      "\"Roast chicken burnt on the bottom, guests at 19:00\" -> title and description kept and in HelpRequested -- B4-S01 US-CA-04 INV-19 R2.E1")
  @Test
  void theRequestShowsTheDishAndWhatHappened() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.title()).isEqualTo("Roast chicken burnt");
    assertThat(request.description())
        .isEqualTo("Roast chicken burnt on the bottom, guests at 19:00");
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> {
              assertThat(requested.title()).isEqualTo("Roast chicken burnt");
              assertThat(requested.description())
                  .isEqualTo("Roast chicken burnt on the bottom, guests at 19:00");
            });
  }

  @DisplayName(
      "Lena adds her recipe \"Roast chicken\" -> the recipe is kept and in HelpRequested -- B4-S01 US-CA-04 INV-19 R2.E2")
  @Test
  void lenaAddsHerRecipe() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR), Optional.of(ROAST_CHICKEN));

    assertThat(request.recipe()).isEqualTo(ROAST_CHICKEN);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> assertThat(requested.recipe()).isEqualTo(ROAST_CHICKEN));
  }

  @DisplayName(
      "Lena gives no recipe -> sent without a recipe, none in HelpRequested -- B4-S01 US-CA-04 INV-19 R2.E3")
  @Test
  void lenaGivesNoRecipe() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));

    assertThat(request.recipe()).isNull();
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class, requested -> assertThat(requested.recipe()).isNull());
  }

  @DisplayName("the rice burns too -> a second, separate request -- B4-S01 US-CA-04 INV-19 R2.E4")
  @Test
  void theRiceBurnsTooIsASecondRequest() {
    HelpRequestId riceRequestId =
        new HelpRequestId(UUID.fromString("01a1d020-0000-7000-8000-00000000a1ce"));
    HelpRequest chicken = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));

    HelpRequest rice =
        HelpRequest.requestHelpWithCatastrophe(
            riceRequestId,
            HelpRequests.LENA,
            "Rice burnt too",
            "The rice burnt as well",
            Optional.empty(),
            List.of(),
            Set.of(HelpProviderType.GRANDMA_AVATAR),
            SATURDAY_18_40);

    assertThat(rice.id()).isEqualTo(riceRequestId).isNotEqualTo(chicken.id());
    assertThat(chicken.pullEvents()).hasSize(1);
    assertThat(rice.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> assertThat(requested.helpRequestId()).isEqualTo(riceRequestId));
  }

  @DisplayName(
      "Lena adds a photo of the burnt bottom -> the photo is kept and in HelpRequested -- B4-S01 US-CA-04 INV-20 R3.E1")
  @Test
  void lenaAddsAPhotoOfTheBurntBottom() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR), Optional.empty(), List.of(BURNT_BOTTOM));

    assertThat(request.pictures()).containsExactly(BURNT_BOTTOM);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            requested -> assertThat(requested.pictures()).containsExactly(BURNT_BOTTOM));
  }

  @DisplayName(
      "11 photos -> rejected with TooManyPictures, rule INV-38, no request -- B4-S01 US-CA-04 INV-20 INV-38")
  @Test
  void elevenPhotosAreTooMany() {
    List<URI> elevenPhotos =
        java.util.stream.IntStream.rangeClosed(1, 11)
            .mapToObj(n -> URI.create("https://photos.example/lena/burnt-" + n + ".jpg"))
            .toList();

    assertThatThrownBy(
            () -> lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR), Optional.empty(), elevenPhotos))
        .isInstanceOfSatisfying(
            TooManyPictures.class, rejected -> assertThat(rejected.rule()).isEqualTo("INV-38"));
  }

  @DisplayName(
      "a photo that is not an https link -> rejected with PictureLinkNotSecure, no request -- B4-S01 US-CA-04 INV-20 INV-38")
  @Test
  void aPhotoIsAnHttpsLink() {
    URI plainHttp = URI.create("http://photos.example/lena/burnt-bottom.jpg");

    assertThatThrownBy(
            () ->
                lenaAsks(
                    Set.of(HelpProviderType.GRANDMA_AVATAR), Optional.empty(), List.of(plainHttp)))
        .isInstanceOfSatisfying(
            PictureLinkNotSecure.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-38"));
  }

  @DisplayName(
      "Grandma answers \"cut off the burnt part, make a fresh sauce\" -> ANSWERED, her Help holds the mitigation, HelpProvided -- B4-S02 US-CA-04 INV-22 R5.E1")
  @Test
  void grandmaAnswersWithStepsToMitigate() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    request.pullEvents();
    CatastropheMitigation rescue =
        new CatastropheMitigation(null, "Cut off the burnt part, make a fresh sauce", List.of());

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's rescue",
        rescue,
        SATURDAY_18_42);

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(request.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.answer()).isEqualTo(rescue));
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpProvided.class, provided -> assertThat(provided.answer()).isEqualTo(rescue));
  }

  @DisplayName(
      "an answer naming another recipe -> AnswerDoesNotMatchRequest, rule INV-37; one without a recipe -> accepted -- B4-S02 US-CA-04 INV-37")
  @Test
  void aMitigationNamesTheRequestsRecipeOrNone() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR), Optional.of(ROAST_CHICKEN));
    RecipeId lasagne = new RecipeId(UUID.fromString("6d2e3f4a-5b6c-4d7e-8f9a-0b1c2d3e4f5a"));

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's rescue",
                    new CatastropheMitigation(lasagne, "Add more sauce", List.of()),
                    SATURDAY_18_42))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejected -> assertThat(rejected.rule()).isEqualTo("INV-37"));
    assertThat(request.helps()).isEmpty();

    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's rescue",
        new CatastropheMitigation(null, "Cut off the burnt part", List.of()),
        SATURDAY_18_42);
    assertThat(request.helps()).hasSize(1);
  }

  @DisplayName(
      "a mitigation on a preparation-step request, an explanation or substitutes on a catastrophe request -> AnswerDoesNotMatchRequest, rule INV-37 -- B4-S02 US-CA-04 INV-37")
  @Test
  void answersOfAnotherKindDoNotMatch() {
    HelpRequest stepRequest = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    assertThatThrownBy(
            () ->
                stepRequest.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's rescue",
                    (HelpAnswer)
                        new CatastropheMitigation(null, "Cut off the burnt part", List.of()),
                    HelpRequests.FRIDAY_18_10))
        .isInstanceOf(AnswerDoesNotMatchRequest.class);

    HelpRequest catastrophe = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    assertThatThrownBy(
            () ->
                catastrophe.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's explanation",
                    (HelpAnswer) HelpRequests.BUTTER_WITH_THREE_PHOTOS,
                    SATURDAY_18_42))
        .isInstanceOf(AnswerDoesNotMatchRequest.class);
    assertThatThrownBy(
            () ->
                catastrophe.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's substitutes",
                    (HelpAnswer)
                        new Substitutes(
                            HelpRequests.TOMATO_SOUP,
                            List.of(HelpRequests.gramsOf("yogurt", "200"))),
                    SATURDAY_18_42))
        .isInstanceOf(AnswerDoesNotMatchRequest.class);
    assertThat(catastrophe.helps()).isEmpty();
  }

  @DisplayName(
      "an answer with 11 photos -> TooManyPictures; with a photo that is not https -> PictureLinkNotSecure -- B4-S02 US-CA-04 INV-38")
  @Test
  void aMitigationCarriesAtMostTenHttpsPhotos() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    List<URI> elevenPhotos =
        java.util.stream.IntStream.rangeClosed(1, 11)
            .mapToObj(n -> URI.create("https://photos.example/grandma/sauce-" + n + ".jpg"))
            .toList();

    assertThatThrownBy(() -> grandmaRescues(request, elevenPhotos))
        .isInstanceOf(TooManyPictures.class);
    assertThatThrownBy(
            () -> grandmaRescues(request, List.of(URI.create("http://photos.example/sauce.jpg"))))
        .isInstanceOf(PictureLinkNotSecure.class);
    assertThat(request.helps()).isEmpty();
  }

  private static void grandmaRescues(HelpRequest request, List<URI> photos) {
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's rescue",
        new CatastropheMitigation(null, "Cut off the burnt part, make a fresh sauce", photos),
        SATURDAY_18_42);
  }

  @DisplayName(
      "Jonas answers 18:54 -> the answer counts; at 18:55:01 -> AnswerTooLate -- B4-S02 US-CA-04 INV-26 R9.E5")
  @Test
  void jonasAnswersAt1854() {
    HelpRequest toAnswerInTime = lenaAsks(Set.of(HelpProviderType.COMMUNITY));
    HelpRequest toAnswerLate = lenaAsks(Set.of(HelpProviderType.COMMUNITY));

    jonasRescues(toAnswerInTime, at("2026-11-14T17:54:00Z"));
    assertThat(toAnswerInTime.helps()).hasSize(1);
    assertThatThrownBy(() -> jonasRescues(toAnswerLate, at("2026-11-14T17:55:01Z")))
        .isInstanceOf(AnswerTooLate.class);
    assertThat(toAnswerLate.helps()).isEmpty();
  }

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  private static void jonasRescues(HelpRequest request, Clock clock) {
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.community(HelpRequests.JONAS),
        "Jonas's rescue",
        new CatastropheMitigation(null, "Serve it with a fresh gravy", List.of()),
        clock);
  }

  @DisplayName(
      "both asked, Grandma answers 18:46 -> AnswerTooLate, while Jonas can still answer -- B4-S02 US-CA-04 INV-26 R9.E3")
  @Test
  void grandmasSideClosesBeforeTheCommunitys() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    Clock at1846 = at("2026-11-14T17:46:00Z");

    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's rescue",
                    new CatastropheMitigation(null, "Cut off the burnt part", List.of()),
                    at1846))
        .isInstanceOf(AnswerTooLate.class);
    jonasRescues(request, at1846);
    assertThat(request.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.providerType()).isEqualTo(HelpProviderType.COMMUNITY));
  }

  @DisplayName(
      "Lena accepts Grandma's answer at 18:44 -> CLOSED, HelpAccepted -- B3-S04 US-CA-04 INV-22 R5.E1")
  @Test
  void lenaAcceptsGrandmasAnswer() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaRescues(request, List.of());
    request.pullEvents();

    request.accept(HelpRequests.HELP_ID, at("2026-11-14T17:44:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpAccepted.class,
            accepted -> assertThat(accepted.helpId()).isEqualTo(HelpRequests.HELP_ID));
  }

  @DisplayName(
      "both asked, Lena accepts Jonas's answer at 18:45 -> CLOSED for Grandma too, her answer -> InvalidHelpRequestState -- B3-S04 US-CA-04 INV-22 R5.E2")
  @Test
  void acceptingJonassAnswerClosesItForGrandmaToo() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    jonasRescues(request, SATURDAY_18_42);

    request.accept(HelpRequests.HELP_ID, at("2026-11-14T17:45:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThatThrownBy(
            () ->
                request.provideHelp(
                    HelpRequests.OTHER_HELP_ID,
                    HelpProvider.grandmaAvatar(),
                    "Grandma's rescue",
                    new CatastropheMitigation(null, "Cut off the burnt part", List.of()),
                    at("2026-11-14T17:45:00Z")))
        .isInstanceOf(InvalidHelpRequestState.class);
  }

  @DisplayName(
      "18:47 Lena rejects Grandma's answer \"still tastes smoky\" -> asked again, Grandma until 18:52, note kept, HelpRejected -- B3-S05 US-CA-04 INV-23 R6.E1")
  @Test
  void lenaRejectsGrandmasAnswerStillSmoky() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    grandmaRescues(request, List.of());
    request.pullEvents();

    request.reject(HelpRequests.HELP_ID, "still tastes smoky", at("2026-11-14T17:47:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR, Instant.parse("2026-11-14T17:52:00Z")));
    assertThat(request.notes()).extracting(Note::text).containsExactly("still tastes smoky");
    assertThat(request.pullEvents()).singleElement().isInstanceOf(HelpRejected.class);
  }

  @DisplayName(
      "both asked, rejected 18:47 -> Community until 19:02, Grandma until 18:52 -- B3-S05 US-CA-04 INV-23 R6.E2")
  @Test
  void bothAreAskedAgainWithTheirOwnDeadlines() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    grandmaRescues(request, List.of());

    request.reject(HelpRequests.HELP_ID, "still tastes smoky", at("2026-11-14T17:47:00Z"));

    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, Instant.parse("2026-11-14T18:02:00Z")),
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR, Instant.parse("2026-11-14T17:52:00Z")));
  }

  @DisplayName(
      "after a rejection an earlier Help not rejected -> InvalidHelpRequestState -- B3-S05 US-CA-04 INV-39 (step 3 r20, O-7)")
  @Test
  void afterARejectionAnEarlierHelpIsSetAside() {
    HelpRequest request = jonasAndGrandmaAnsweredAndLenaRejectedGrandmas();

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThatThrownBy(() -> request.accept(HelpRequests.HELP_ID, at("2026-11-14T17:48:00Z")))
        .isInstanceOf(InvalidHelpRequestState.class);
  }

  /** Jonas answered (HELP_ID), Grandma answered (OTHER_HELP_ID), Lena rejected Grandma's. */
  private static HelpRequest jonasAndGrandmaAnsweredAndLenaRejectedGrandmas() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    jonasRescues(request, SATURDAY_18_42);
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's rescue",
        new CatastropheMitigation(null, "Cut off the burnt part", List.of()),
        SATURDAY_18_42);
    request.reject(HelpRequests.OTHER_HELP_ID, "still tastes smoky", at("2026-11-14T17:47:00Z"));
    return request;
  }

  @DisplayName(
      "after a rejection a new answer arrives -> the earlier Help not rejected can be accepted -- B3-S04 US-CA-04 INV-39 (step 3 r20, O-7)")
  @Test
  void aNewAnswerMakesTheEarlierHelpAcceptableAgain() {
    HelpRequest request = jonasAndGrandmaAnsweredAndLenaRejectedGrandmas();
    HelpId grandmasSecondTry = new HelpId(UUID.fromString("01a1d021-0000-7000-8000-0000000000b2"));
    request.provideHelp(
        grandmasSecondTry,
        HelpProvider.grandmaAvatar(),
        "Grandma's second try",
        new CatastropheMitigation(null, "Serve it with a sharp salad", List.of()),
        at("2026-11-14T17:49:00Z"));

    request.accept(HelpRequests.HELP_ID, at("2026-11-14T17:50:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
  }

  @DisplayName(
      "18:50 Lena cancels: \"ordered pizza\" -> CANCELLED, note kept and in HelpRequestCancelled -- B3-S03 US-CA-04 INV-25 R8.E1")
  @Test
  void lenaCancelsOrderedPizza() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.COMMUNITY));
    request.pullEvents();

    request.cancel(Optional.of("ordered pizza"), at("2026-11-14T17:50:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.notes()).extracting(Note::text).containsExactly("ordered pizza");
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class,
            cancelled -> assertThat(cancelled.note()).contains("ordered pizza"));
  }

  @DisplayName(
      "only the Community asked 18:40, silent until 18:55 -> CANCELLED, Grandma suggested -- B3-S06 US-CA-04 INV-26 R9.E1")
  @Test
  void onlyTheCommunityStaysSilent() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.COMMUNITY));
    request.pullEvents();

    request.timeOut(at("2026-11-14T17:55:01Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestTimedOut.class,
            timedOut ->
                assertThat(timedOut.suggestedProviders())
                    .containsExactly(HelpProviderType.GRANDMA_AVATAR));
  }

  @DisplayName(
      "only Grandma asked 18:40, silent until 18:45 -> CANCELLED, Community suggested -- B3-S06 US-CA-04 INV-26 R9.E2")
  @Test
  void onlyGrandmaStaysSilent() {
    HelpRequest request = lenaAsks(Set.of(HelpProviderType.GRANDMA_AVATAR));
    request.pullEvents();

    request.timeOut(at("2026-11-14T17:45:01Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestTimedOut.class,
            timedOut ->
                assertThat(timedOut.suggestedProviders())
                    .containsExactly(HelpProviderType.COMMUNITY));
  }

  @DisplayName(
      "both asked, a pass after 18:45 -> Grandma's side TIMED_OUT, the Community's ASKED, request OPEN, no event -- B3-S06 US-CA-04 INV-26 R9.E3 (step 3 r21)")
  @Test
  void grandmasSideTimesOutAlone() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    request.pullEvents();

    request.timeOut(at("2026-11-14T17:46:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, SATURDAY_18_55, ProviderState.ASKED),
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR, SATURDAY_18_45, ProviderState.TIMED_OUT));
    assertThat(request.pullEvents()).isEmpty();
  }

  @DisplayName(
      "both silent until 18:55 -> CANCELLED, both TIMED_OUT, no suggestion, HelpRequestTimedOut -- B3-S06 US-CA-04 INV-26 R9.E4")
  @Test
  void bothStaySilentUntil1855() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    request.pullEvents();

    request.timeOut(at("2026-11-14T17:46:00Z"));
    request.timeOut(at("2026-11-14T17:55:01Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(request.askedProviders())
        .extracting(AskedProvider::state)
        .containsOnly(ProviderState.TIMED_OUT);
    assertThat(request.pullEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestTimedOut.class,
            timedOut -> assertThat(timedOut.suggestedProviders()).isEmpty());
  }

  @DisplayName("a time-out pass before any deadline -> DeadlineNotPassed -- B3-S06 US-CA-04 INV-26")
  @Test
  void aPassBeforeAnyDeadlineTimesNothingOut() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));

    assertThatThrownBy(() -> request.timeOut(at("2026-11-14T17:41:00Z")))
        .isInstanceOf(DeadlineNotPassed.class);
    assertThat(request.askedProviders())
        .extracting(AskedProvider::state)
        .containsOnly(ProviderState.ASKED);
  }

  @DisplayName(
      "after Grandma's side timed out the Community answers -> ANSWERED, Grandma's side stays TIMED_OUT -- B3-S06 US-CA-04 INV-26 R9.E3 (step 3 r21)")
  @Test
  void theCommunityAnswersAfterGrandmasSideTimedOut() {
    HelpRequest request =
        lenaAsks(Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));
    request.timeOut(at("2026-11-14T17:46:00Z"));

    jonasRescues(request, at("2026-11-14T17:50:00Z"));

    assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(request.askedProviders())
        .containsExactlyInAnyOrder(
            new AskedProvider(HelpProviderType.COMMUNITY, SATURDAY_18_55, ProviderState.ANSWERED),
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR, SATURDAY_18_45, ProviderState.TIMED_OUT));
  }
}
