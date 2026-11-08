package org.larder.cookingassistance.domain.helprequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Help Request aggregate — increment 0, walking skeleton (US-CA-03, B3-S01 + B3-S02).
 * Specification: docs/cooking-assistance/design/invariants.md (INV-01..04, INV-36..38),
 * design-brief.md, api/cooking-assistance.asyncapi.yaml (HelpRequestCreated, HelpProvided). Cast
 * from the Example Map: Lena (cook), Tom (cook, free plan), Jonas and Mia (Community), Grandma
 * Avatar, Chef Marco.
 */
class HelpRequestTest {

  static final HelpRequestId HELP_REQUEST_ID =
      new HelpRequestId(UUID.fromString("23a8eeed-35f6-460b-892e-7bb458a8fded"));
  static final CookId LENA = new CookId(UUID.fromString("f64e07f0-f9e7-4b7a-8695-e7a8b3ef5074"));
  static final RecipeId HOLLANDAISE =
      new RecipeId(UUID.fromString("7cf09822-77a1-46bb-812f-b7852bca0913"));
  static final HowToStepId STEP_4 =
      new HowToStepId(UUID.fromString("65610dee-fb83-4341-a730-26a4a99a621a"));
  static final HowToStepId STEP_6 =
      new HowToStepId(UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-2a3b4c5d6e7f"));
  static final HelpRequestId OTHER_HELP_REQUEST_ID =
      new HelpRequestId(UUID.fromString("0b8f7a52-4bfa-4d4e-9d6d-1f4b9c8f9a10"));

  /** Tom, a cook on the free plan. */
  static final CookId TOM = new CookId(UUID.fromString("5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d"));

  static final RecipeId BREAD =
      new RecipeId(UUID.fromString("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"));
  static final HowToStepId STEP_2 =
      new HowToStepId(UUID.fromString("b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e"));
  static final HelpId HELP_ID = new HelpId(UUID.fromString("a9caf90d-00b4-4a66-8184-7d02152e8d6a"));
  static final HelpId OTHER_HELP_ID =
      new HelpId(UUID.fromString("1e7c2a90-3a5b-4b1d-8f0e-7c2d9b1a4e22"));

  /** Jonas, a Community member. */
  static final CookId JONAS = new CookId(UUID.fromString("39a7aed5-2e50-48c8-8aa6-f3afa9f03f74"));

  /** Mia, a Community member. */
  static final CookId MIA = new CookId(UUID.fromString("4c5d6e7f-8a9b-4c0d-9e1f-2a3b4c5d6e70"));

  /** Friday 18:00 CET. */
  static final Clock FRIDAY_18_00 =
      Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneOffset.UTC);

  /** Friday 18:10 CET. */
  static final Clock FRIDAY_18_10 =
      Clock.fixed(Instant.parse("2026-11-06T17:10:00Z"), ZoneOffset.UTC);

  static final PreparationStepExplanation BUTTER_IN_A_THIN_STREAM =
      new PreparationStepExplanation(
          HOLLANDAISE, STEP_4, "Add the butter in a thin stream", List.of());

  /** A clock fixed at the given local time in Larder's zone (UTC+1 in November). */
  static Clock at(String localDateTime) {
    return Clock.fixed(
        java.time.LocalDateTime.parse(localDateTime).toInstant(ZoneOffset.ofHours(1)),
        ZoneOffset.UTC);
  }

  /** Links to n distinct pictures in Larder's media store. */
  static List<URI> photos(int n) {
    return java.util.stream.IntStream.rangeClosed(1, n)
        .mapToObj(i -> URI.create("https://larder.org/media/images/photo-" + i))
        .toList();
  }

  /** An explanation of step 4 of the Hollandaise with the given pictures. */
  static PreparationStepExplanation explanationWith(List<URI> pictures) {
    return new PreparationStepExplanation(
        HOLLANDAISE, STEP_4, "Add the butter in a thin stream", pictures);
  }

  /** Lena's stored request about step 4, asked of Grandma Avatar, in the given status. */
  static HelpRequest storedRequestToGrandmaIn(HelpRequestStatus status) {
    return storedRequestIn(status, HelpProviderType.GRANDMA_AVATAR);
  }

  /** Lena's stored request about step 4, asked of the given provider, in the given status. */
  static HelpRequest storedRequestIn(HelpRequestStatus status, HelpProviderType askedProvider) {
    return HelpRequest.reconstitute(
        HELP_REQUEST_ID,
        LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HelpType.PREPARATION_STEP_EXPLANATION,
        HOLLANDAISE,
        STEP_4,
        List.of(new AskedProvider(askedProvider, Instant.parse("2026-11-07T05:00:00Z"))),
        status,
        List.of(),
        List.of(),
        1L);
  }

  /** Grandma Avatar explains step 4 on Friday 18:10. */
  static void grandmaExplainsStep4(HelpRequest request) {
    request.provideHelp(
        HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        BUTTER_IN_A_THIN_STREAM,
        FRIDAY_18_10);
  }

  /**
   * Grandma explained step 4 (Fri 18:10), Lena rejected it (Fri 20:00), Grandma answered again (Fri
   * 20:30); events pulled.
   */
  static HelpRequest grandmaAnsweredTwiceAfterARejection() {
    HelpRequest request = lenaAsksGrandmaAboutStep4();
    grandmaExplainsStep4(request);
    request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));
    request.provideHelp(
        OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Cool the butter first",
        BUTTER_IN_A_THIN_STREAM,
        at("2026-11-06T20:30"));
    request.pullEvents();
    return request;
  }

  /** Lena asks Grandma Avatar about step 4 of her Hollandaise on Friday 18:00. */
  static HelpRequest lenaAsksGrandmaAboutStep4() {
    return lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
  }

  /** Lena asks the given providers about step 4 of her Hollandaise on Friday 18:00. */
  static HelpRequest lenaAsksAboutStep4(HelpProviderType... providers) {
    return HelpRequest.requestHelpWithPreparationStep(
        HELP_REQUEST_ID,
        LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HOLLANDAISE,
        STEP_4,
        Set.of(providers),
        FRIDAY_18_00);
  }

  @Nested
  class RequestHelpWithPreparationStep { // B3-S01, US-CA-03

    @DisplayName(
        "Lena asks Grandma Avatar about step 4 of Hollandaise -> request is OPEN, requester Lena, type PREPARATION_STEP_EXPLANATION, recipe Hollandaise, step 4, Grandma Avatar asked -- B3-S01 US-CA-03 INV-01 R1.E1, INV-02 R2.E1")
    @Test
    void requestToGrandmaIsOpenWithItsRecipeStepAndProvider() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.requester()).isEqualTo(LENA);
      assertThat(request.type()).isEqualTo(HelpType.PREPARATION_STEP_EXPLANATION);
      assertThat(request.recipe()).isEqualTo(HOLLANDAISE);
      assertThat(request.howToStep()).isEqualTo(STEP_4);
      assertThat(request.askedProviderTypes()).containsExactly(HelpProviderType.GRANDMA_AVATAR);
    }

    @DisplayName(
        "requesting records exactly one HelpRequested event carrying helpRequestId, requester, title, type, description, recipe, howToStep, askedProviders, status OPEN, occurredAt -- B3-S01 US-CA-03 evt HelpRequested (AsyncAPI HelpRequestCreated)")
    @Test
    void requestingRecordsOneHelpRequestedEventWithThePayload() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      List<HelpRequestEvent> events = request.pullEvents();

      assertThat(events).hasSize(1);
      assertThat(events.getFirst()).isInstanceOf(HelpRequested.class);
      HelpRequested requested = (HelpRequested) events.getFirst();
      assertThat(requested.helpRequestId()).isEqualTo(HELP_REQUEST_ID);
      assertThat(requested.requester()).isEqualTo(LENA);
      assertThat(requested.title()).isEqualTo("How do I keep the sauce from splitting?");
      assertThat(requested.type()).isEqualTo(HelpType.PREPARATION_STEP_EXPLANATION);
      assertThat(requested.description()).isEqualTo("Step 4 of my Hollandaise keeps splitting");
      assertThat(requested.recipe()).isEqualTo(HOLLANDAISE);
      assertThat(requested.howToStep()).isEqualTo(STEP_4);
      assertThat(requested.askedProviders())
          .extracting(AskedProvider::providerType)
          .containsExactly(HelpProviderType.GRANDMA_AVATAR);
      assertThat(requested.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(requested.occurredAt()).isEqualTo(Instant.parse("2026-11-06T17:00:00Z"));
    }

    @DisplayName(
        "Grandma Avatar asked Fri 18:00 -> Grandma's deadline Sat 06:00 (12 h) -- B3-S01 US-CA-03 INV-09 R8.E2 (deadline only; timeout in increment 1)")
    @Test
    void askedProviderGetsADeadlineTwelveHoursAfterTheRequest() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThat(request.askedProviders())
          .containsExactly(
              new AskedProvider(
                  HelpProviderType.GRANDMA_AVATAR, Instant.parse("2026-11-07T05:00:00Z")));
    }

    @DisplayName(
        "Lena asks the Community and Grandma Avatar together -> both are asked, both with the same deadline -- B3-S01 US-CA-03 INV-01 R1.E2")
    @Test
    void communityAndGrandmaAreAskedTogether() {
      HelpRequest request =
          lenaAsksAboutStep4(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);

      Instant saturday0600 = Instant.parse("2026-11-07T05:00:00Z");
      assertThat(request.askedProviders())
          .containsExactlyInAnyOrder(
              new AskedProvider(HelpProviderType.COMMUNITY, saturday0600),
              new AskedProvider(HelpProviderType.GRANDMA_AVATAR, saturday0600));
    }

    @DisplayName(
        "Tom (free plan) asks the Community about step 2 of his bread -> request is created, no premium needed -- B3-S01 US-CA-03 INV-01 R1.E3")
    @Test
    void everyCookCanAskTheCommunity() {
      HelpRequest request =
          HelpRequest.requestHelpWithPreparationStep(
              OTHER_HELP_REQUEST_ID,
              TOM,
              "Dough too sticky",
              "Step 2 of my bread: the dough sticks to everything",
              BREAD,
              STEP_2,
              Set.of(HelpProviderType.COMMUNITY),
              FRIDAY_18_00);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.requester()).isEqualTo(TOM);
      assertThat(request.recipe()).isEqualTo(BREAD);
      assertThat(request.howToStep()).isEqualTo(STEP_2);
      assertThat(request.askedProviderTypes()).containsExactly(HelpProviderType.COMMUNITY);
    }

    @DisplayName(
        "Lena asks Chef Marco about step 4 -> rejected with ChefNotAllowed (INV-01, code CHEF_NOT_ALLOWED), no event -- B3-S01 US-CA-03 INV-01 R1.E4")
    @Test
    void askingAChefIsRejected() {
      assertThatThrownBy(() -> lenaAsksAboutStep4(HelpProviderType.CHEF))
          .isInstanceOfSatisfying(
              ChefNotAllowed.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-01");
                assertThat(rejection.code()).isEqualTo("CHEF_NOT_ALLOWED");
              });
    }

    @DisplayName(
        "Lena asks Chef Marco together with the Community -> rejected with ChefNotAllowed (INV-01) -- B3-S01 US-CA-03 INV-01 R1.E4")
    @Test
    void askingAChefAlongsideTheCommunityIsRejected() {
      assertThatThrownBy(
              () -> lenaAsksAboutStep4(HelpProviderType.CHEF, HelpProviderType.COMMUNITY))
          .isInstanceOf(ChefNotAllowed.class);
    }

    @DisplayName(
        "Lena asks nobody -> rejected with NoHelpProviderChosen (INV-01, preferred Provider 1..2) -- B3-S01 US-CA-03 INV-01 glossary 1..2")
    @Test
    void askingNobodyIsRejected() {
      assertThatThrownBy(() -> lenaAsksAboutStep4())
          .isInstanceOfSatisfying(
              NoHelpProviderChosen.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-01");
                assertThat(rejection.code()).isEqualTo("VALIDATION_FAILED");
              });
    }

    @DisplayName(
        "request without a recipe -> rejected with RecipeRequired (INV-02, code MISSING_FIELD) -- B3-S01 US-CA-03 INV-02")
    @Test
    void requestWithoutRecipeIsRejected() {
      assertThatThrownBy(
              () ->
                  HelpRequest.requestHelpWithPreparationStep(
                      HELP_REQUEST_ID,
                      LENA,
                      "How do I keep the sauce from splitting?",
                      "Step 4 keeps splitting",
                      null,
                      STEP_4,
                      Set.of(HelpProviderType.GRANDMA_AVATAR),
                      FRIDAY_18_00))
          .isInstanceOfSatisfying(
              RecipeRequired.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-02");
                assertThat(rejection.code()).isEqualTo("MISSING_FIELD");
              });
    }

    @DisplayName(
        "request without a step -> rejected with PreparationStepRequired (INV-02, code MISSING_FIELD) -- B3-S01 US-CA-03 INV-02")
    @Test
    void requestWithoutStepIsRejected() {
      assertThatThrownBy(
              () ->
                  HelpRequest.requestHelpWithPreparationStep(
                      HELP_REQUEST_ID,
                      LENA,
                      "How do I keep the sauce from splitting?",
                      "Step 4 keeps splitting",
                      HOLLANDAISE,
                      null,
                      Set.of(HelpProviderType.GRANDMA_AVATAR),
                      FRIDAY_18_00))
          .isInstanceOfSatisfying(
              PreparationStepRequired.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-02");
                assertThat(rejection.code()).isEqualTo("MISSING_FIELD");
              });
    }

    @DisplayName(
        "unsure about steps 4 and 6 -> two requests, one with step 4 and one with step 6, different ids -- B3-S01 US-CA-03 INV-02 R2.E2")
    @Test
    void twoStepsMakeTwoRequests() {
      HelpRequest aboutStep4 = lenaAsksGrandmaAboutStep4();
      HelpRequest aboutStep6 =
          HelpRequest.requestHelpWithPreparationStep(
              OTHER_HELP_REQUEST_ID,
              LENA,
              "When is the butter warm enough?",
              "Step 6 of my Hollandaise",
              HOLLANDAISE,
              STEP_6,
              Set.of(HelpProviderType.GRANDMA_AVATAR),
              FRIDAY_18_00);

      assertThat(aboutStep4.howToStep()).isEqualTo(STEP_4);
      assertThat(aboutStep6.howToStep()).isEqualTo(STEP_6);
      assertThat(aboutStep4.id()).isEqualTo(HELP_REQUEST_ID);
      assertThat(aboutStep6.id()).isEqualTo(OTHER_HELP_REQUEST_ID);
    }
  }

  @Nested
  class ProvideHelp { // B3-S02, US-CA-03

    @DisplayName(
        "Grandma Avatar explains step 4 ('butter in a thin stream') -> one Help with that answer, provider type GRANDMA_AVATAR, no individual provider, not accepted; request ANSWERED; Grandma's side ANSWERED -- B3-S02 US-CA-03 INV-04 R4.E1")
    @Test
    void grandmaExplainsTheStepAndTheRequestIsAnswered() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      grandmaExplainsStep4(request);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps())
          .singleElement()
          .satisfies(
              help -> {
                assertThat(help.id()).isEqualTo(HELP_ID);
                assertThat(help.providerType()).isEqualTo(HelpProviderType.GRANDMA_AVATAR);
                assertThat(help.provider()).isEmpty();
                assertThat(help.answerTitle()).isEqualTo("Butter in a thin stream");
                assertThat(help.answer()).isEqualTo(BUTTER_IN_A_THIN_STREAM);
                assertThat(help.isAccepted()).isFalse();
              });
      assertThat(request.askedProviders())
          .singleElement()
          .extracting(AskedProvider::state)
          .isEqualTo(ProviderState.ANSWERED);
    }

    @DisplayName(
        "providing help records exactly one HelpProvided event carrying helpId, helpRequest, helpRequester, answerTitle, helpProviderType, helpProvider (absent for Grandma), answer, helpRequestStatus ANSWERED, occurredAt -- B3-S02 US-CA-03 evt HelpProvided")
    @Test
    void providingHelpRecordsOneHelpProvidedEventWithThePayload() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      grandmaExplainsStep4(request);

      List<HelpProvided> provided =
          request.pullEvents().stream()
              .filter(HelpProvided.class::isInstance)
              .map(HelpProvided.class::cast)
              .toList();
      assertThat(provided).hasSize(1);
      HelpProvided event = provided.getFirst();
      assertThat(event.helpId()).isEqualTo(HELP_ID);
      assertThat(event.helpRequest()).isEqualTo(HELP_REQUEST_ID);
      assertThat(event.helpRequester()).isEqualTo(LENA);
      assertThat(event.answerTitle()).isEqualTo("Butter in a thin stream");
      assertThat(event.helpProviderType()).isEqualTo(HelpProviderType.GRANDMA_AVATAR);
      assertThat(event.helpProvider()).isEmpty();
      assertThat(event.answer()).isEqualTo(BUTTER_IN_A_THIN_STREAM);
      assertThat(event.helpRequestStatus()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-06T17:10:00Z"));
    }

    @DisplayName(
        "Grandma answers with 'butter in a thin stream' + 3 photos -> the Help carries the answer and the 3 photos -- B3-S02 US-CA-03 INV-04 R4.E1")
    @Test
    void anAnswerMayCarryPictures() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      List<URI> threePhotos = photos(3);

      request.provideHelp(
          HELP_ID,
          HelpProvider.grandmaAvatar(),
          "Butter in a thin stream",
          new PreparationStepExplanation(
              HOLLANDAISE, STEP_4, "Add the butter in a thin stream", threePhotos),
          FRIDAY_18_10);

      assertThat(request.helps())
          .singleElement()
          .satisfies(help -> assertThat(help.answer().images()).isEqualTo(threePhotos));
    }

    @DisplayName(
        "Jonas and Mia (Community) both answer -> two Helps, request stays ANSWERED, Jonas and Mia recorded as providers -- B3-S02 US-CA-03 INV-04 R4.E3")
    @Test
    void anAnsweredRequestCanStillBeAnswered() {
      HelpRequest request = lenaAsksAboutStep4(HelpProviderType.COMMUNITY);

      request.provideHelp(
          HELP_ID,
          HelpProvider.community(JONAS),
          "Warm the bowl",
          new PreparationStepExplanation(HOLLANDAISE, STEP_4, "Warm the bowl first", List.of()),
          FRIDAY_18_10);
      request.provideHelp(
          OTHER_HELP_ID,
          HelpProvider.community(MIA),
          "Butter in a thin stream",
          BUTTER_IN_A_THIN_STREAM,
          FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps())
          .extracting(Help::provider)
          .containsExactly(Optional.of(JONAS), Optional.of(MIA));
    }

    @DisplayName("answer with exactly 10 photos -> accepted -- B3-S02 US-CA-03 INV-38 boundary")
    @Test
    void tenPicturesAreAllowed() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      request.provideHelp(
          HELP_ID,
          HelpProvider.grandmaAvatar(),
          "Butter",
          explanationWith(photos(10)),
          FRIDAY_18_10);

      assertThat(request.helps())
          .singleElement()
          .satisfies(help -> assertThat(help.answer().images()).hasSize(10));
    }

    @DisplayName(
        "Grandma's answer carries 11 photos -> rejected with TooManyPictures (INV-38), no Help, no event -- B3-S02 US-CA-03 INV-38 S3.E3")
    @Test
    void elevenPicturesAreRejected() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "Butter",
                      explanationWith(photos(11)),
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              TooManyPictures.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-38");
                assertThat(rejection.code()).isEqualTo("TOO_MANY_PICTURES");
              });
      assertThat(request.helps()).isEmpty();
      assertThat(request.pullEvents()).noneMatch(HelpProvided.class::isInstance);
    }

    @DisplayName(
        "Grandma's answer carries a javascript: picture link -> rejected with PictureLinkNotSecure (INV-38, code INVALID_PICTURE_LINK), no Help, no event -- B3-S02 US-CA-03 INV-38 OWASP-API10")
    @Test
    void pictureLinksAreHttpsOnly() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "Butter",
                      explanationWith(List.of(URI.create("javascript:alert(1)"))),
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              PictureLinkNotSecure.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-38");
                assertThat(rejection.code()).isEqualTo("INVALID_PICTURE_LINK");
              });
      assertThat(request.helps()).isEmpty();
      assertThat(request.pullEvents()).noneMatch(HelpProvided.class::isInstance);
    }

    @DisplayName(
        "Lena tries to answer her own request about step 4 -> rejected with OwnRequest (INV-36, code OWN_REQUEST), no Help -- B3-S02 US-CA-03 INV-36 S3.E1")
    @Test
    void aCookNeverAnswersTheirOwnRequest() {
      HelpRequest request = lenaAsksAboutStep4(HelpProviderType.COMMUNITY);

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.community(LENA),
                      "Never mind",
                      BUTTER_IN_A_THIN_STREAM,
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              OwnRequest.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-36");
                assertThat(rejection.code()).isEqualTo("OWN_REQUEST");
              });
      assertThat(request.helps()).isEmpty();
    }

    @DisplayName(
        "Grandma answers Lena's step-4 request with an explanation of step 6 -> rejected with AnswerDoesNotMatchRequest (INV-37) -- B3-S02 US-CA-03 INV-37 S3.E2")
    @Test
    void anAnswerAboutAnotherStepIsRejected() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "About step 6",
                      new PreparationStepExplanation(
                          HOLLANDAISE, STEP_6, "Warm the butter to 60 degrees", List.of()),
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              AnswerDoesNotMatchRequest.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-37");
                assertThat(rejection.code()).isEqualTo("ANSWER_DOES_NOT_MATCH_REQUEST");
              });
      assertThat(request.helps()).isEmpty();
    }

    @DisplayName(
        "an answer about the same step number of another recipe -> rejected with AnswerDoesNotMatchRequest (INV-37) -- B3-S02 US-CA-03 INV-37 (recipe dimension)")
    @Test
    void anAnswerAboutAnotherRecipeIsRejected() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "About bread",
                      new PreparationStepExplanation(BREAD, STEP_4, "Knead longer", List.of()),
                      FRIDAY_18_10))
          .isInstanceOf(AnswerDoesNotMatchRequest.class);
      assertThat(request.helps()).isEmpty();
    }

    @DisplayName(
        "request sent only to Grandma Avatar; Jonas (Community) answers -> rejected with ProviderNotAsked (code PROVIDER_NOT_ASKED) -- B3-S02 US-CA-03 brief precondition, spec provideHelp 403")
    @Test
    void aProviderWhoWasNotAskedCannotAnswer() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.community(JONAS),
                      "Warm the bowl",
                      BUTTER_IN_A_THIN_STREAM,
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              ProviderNotAsked.class,
              rejection -> assertThat(rejection.code()).isEqualTo("PROVIDER_NOT_ASKED"));
      assertThat(request.helps()).isEmpty();
    }

    @DisplayName(
        "a CANCELLED request (reconstituted) receives Grandma's answer -> rejected with InvalidHelpRequestState (code INVALID_STATE), no Help -- B3-S02 US-CA-03 state machine (forbidden transition)")
    @Test
    void aCancelledRequestCannotBeAnswered() {
      HelpRequest cancelled = storedRequestToGrandmaIn(HelpRequestStatus.CANCELLED);

      assertThatThrownBy(() -> grandmaExplainsStep4(cancelled))
          .isInstanceOfSatisfying(
              InvalidHelpRequestState.class,
              rejection -> assertThat(rejection.code()).isEqualTo("INVALID_STATE"));
      assertThat(cancelled.helps()).isEmpty();
    }

    @DisplayName(
        "a CLOSED request (reconstituted) receives Jonas's answer -> rejected with InvalidHelpRequestState (code INVALID_STATE) -- B3-S02 US-CA-03 state machine (forbidden transition)")
    @Test
    void aClosedRequestCannotBeAnswered() {
      HelpRequest closed = storedRequestIn(HelpRequestStatus.CLOSED, HelpProviderType.COMMUNITY);

      assertThatThrownBy(
              () ->
                  closed.provideHelp(
                      HELP_ID,
                      HelpProvider.community(JONAS),
                      "Warm the bowl",
                      BUTTER_IN_A_THIN_STREAM,
                      FRIDAY_18_10))
          .isInstanceOfSatisfying(
              InvalidHelpRequestState.class,
              rejection -> assertThat(rejection.code()).isEqualTo("INVALID_STATE"));
      assertThat(closed.helps()).isEmpty();
    }

    @DisplayName(
        "only Community asked Fri 18:00 (deadline Sat 06:00); Jonas answers Sat 05:59 -> the answer counts: one Help, request ANSWERED, Community's side ANSWERED -- B3-S02 US-CA-03 INV-09 R8.E4")
    @Test
    void jonasAnswersAMinuteBeforeTheDeadline() {
      HelpRequest request = lenaAsksAboutStep4(HelpProviderType.COMMUNITY);

      request.provideHelp(
          HELP_ID,
          HelpProvider.community(JONAS),
          "Warm the bowl",
          BUTTER_IN_A_THIN_STREAM,
          at("2026-11-07T05:59"));

      assertThat(request.helps()).singleElement().extracting(Help::id).isEqualTo(HELP_ID);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.askedProviders())
          .singleElement()
          .extracting(AskedProvider::state)
          .isEqualTo(ProviderState.ANSWERED);
    }

    @DisplayName(
        "Grandma answers at Sat 06:00:00, exactly her deadline -> the answer counts, request ANSWERED -- B3-S02 US-CA-03 INV-09 boundary (step 3 r5)")
    @Test
    void answerAtTheDeadlineCounts() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      request.provideHelp(
          HELP_ID,
          HelpProvider.grandmaAvatar(),
          "Butter in a thin stream",
          BUTTER_IN_A_THIN_STREAM,
          at("2026-11-07T06:00:00"));

      assertThat(request.helps()).singleElement().extracting(Help::id).isEqualTo(HELP_ID);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
    }

    @DisplayName(
        "stored request asked of the Community (deadline Sat 06:00, passed) and Grandma (deadline Sat 08:00); Grandma answers Sat 07:00 -> the answer counts: only the answering provider's own deadline decides -- B3-S02 US-CA-03 INV-09 per-provider state")
    @Test
    void onlyTheAnsweringProvidersDeadlineCounts() {
      HelpRequest request =
          HelpRequest.reconstitute(
              HELP_REQUEST_ID,
              LENA,
              "How do I keep the sauce from splitting?",
              "Step 4 of my Hollandaise keeps splitting",
              HelpType.PREPARATION_STEP_EXPLANATION,
              HOLLANDAISE,
              STEP_4,
              List.of(
                  new AskedProvider(HelpProviderType.COMMUNITY, at("2026-11-07T06:00").instant()),
                  new AskedProvider(
                      HelpProviderType.GRANDMA_AVATAR, at("2026-11-07T08:00").instant())),
              HelpRequestStatus.OPEN,
              List.of(),
              List.of(),
              1L);

      request.provideHelp(
          HELP_ID,
          HelpProvider.grandmaAvatar(),
          "Butter in a thin stream",
          BUTTER_IN_A_THIN_STREAM,
          at("2026-11-07T07:00"));

      assertThat(request.helps()).singleElement().extracting(Help::id).isEqualTo(HELP_ID);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
    }

    @DisplayName(
        "Grandma answers at Sat 06:00:01, after her deadline -> AnswerTooLate (rule INV-09), no Help, request stays OPEN, no event -- B3-S02 US-CA-03 INV-09 (step 3 r5)")
    @Test
    void answerAfterTheDeadlineIsTooLate() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "Butter in a thin stream",
                      BUTTER_IN_A_THIN_STREAM,
                      at("2026-11-07T06:00:01")))
          .isInstanceOfSatisfying(
              AnswerTooLate.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-09");
                assertThat(rejection.code()).isEqualTo("INVALID_STATE");
              });
      assertThat(request.helps()).isEmpty();
      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.pullEvents()).isEmpty();
    }
  }

  @Nested
  class CancelHelpRequest { // B3-S03, US-CA-03

    @DisplayName(
        "Lena cancels her open request at Fri 18:10 with note 'an ice cube saved it' -> CANCELLED; HelpRequestCancelled {helpRequest, helpRequester Lena, note 'an ice cube saved it', askedProviders [Grandma, deadline Sat 06:00], CANCELLED, occurredAt Fri 18:10} -- B3-S03 US-CA-03 INV-08 R7.E1")
    @Test
    void lenaCancelsWithANote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      request.cancel(Optional.of("an ice cube saved it"), FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.pullEvents())
          .containsExactly(
              new HelpRequestCancelled(
                  HELP_REQUEST_ID,
                  LENA,
                  Optional.of("an ice cube saved it"),
                  List.of(
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR, at("2026-11-07T06:00").instant())),
                  HelpRequestStatus.CANCELLED,
                  FRIDAY_18_10.instant()));
    }

    @DisplayName(
        "Lena cancels without a note -> CANCELLED; HelpRequestCancelled without a note -- B3-S03 US-CA-03 INV-08 R7.E2")
    @Test
    void lenaCancelsWithoutANote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      request.cancel(Optional.empty(), FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              HelpRequestCancelled.class, cancelled -> assertThat(cancelled.note()).isEmpty());
    }

    @DisplayName(
        "Lena cancels with the note '   ' (blank) -> treated as no note: HelpRequestCancelled without a note, no notes kept -- B3-S03 US-CA-03 INV-08 R7.E2 (step 13, D-3)")
    @Test
    void blankCancellationNoteIsNoNote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      request.cancel(Optional.of("   "), FRIDAY_18_10);

      assertThat(request.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              HelpRequestCancelled.class, cancelled -> assertThat(cancelled.note()).isEmpty());
      assertThat(request.notes()).isEmpty();
    }

    @DisplayName(
        "Grandma answered; Lena cancels -> CANCELLED (Open or Answered may be cancelled) -- B3-S03 US-CA-03 INV-08")
    @Test
    void answeredRequestCanBeCancelled() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);

      request.cancel(Optional.empty(), FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    }

    @DisplayName(
        "Lena cancelled; Grandma answers -> InvalidHelpRequestState(CANCELLED), no Help -- B3-S03 US-CA-03 INV-08 lifecycle")
    @Test
    void cancelledRequestTakesNoAnswer() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.cancel(Optional.empty(), FRIDAY_18_10);

      assertThatThrownBy(() -> grandmaExplainsStep4(request))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CANCELLED");
      assertThat(request.helps()).isEmpty();
    }

    @DisplayName(
        "cancel a CLOSED request -> InvalidHelpRequestState(CLOSED), status unchanged, no event -- B3-S03 US-CA-03 lifecycle")
    @Test
    void closedRequestCannotBeCancelled() {
      HelpRequest closed = storedRequestToGrandmaIn(HelpRequestStatus.CLOSED);

      assertThatThrownBy(() -> closed.cancel(Optional.empty(), FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CLOSED");
      assertThat(closed.status()).isEqualTo(HelpRequestStatus.CLOSED);
      assertThat(closed.pullEvents()).isEmpty();
    }

    @DisplayName(
        "cancel a CANCELLED request -> InvalidHelpRequestState(CANCELLED), no event -- B3-S03 US-CA-03 lifecycle")
    @Test
    void cancelledRequestCannotBeCancelledAgain() {
      HelpRequest cancelled = storedRequestToGrandmaIn(HelpRequestStatus.CANCELLED);

      assertThatThrownBy(() -> cancelled.cancel(Optional.of("again"), FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CANCELLED");
      assertThat(cancelled.pullEvents()).isEmpty();
    }
  }

  @Nested
  class AcceptHelp { // B3-S04, US-CA-03

    @DisplayName(
        "Grandma answered; Lena accepts Grandma's Help at Fri 18:10 -> CLOSED, that Help accepted; HelpAccepted {helpRequest, helpRequester Lena, helpId, GRANDMA_AVATAR, no helpProvider, CLOSED, occurredAt Fri 18:10} -- B3-S04 US-CA-03 INV-05 R4.E2")
    @Test
    void lenaAcceptsGrandmasAnswer() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      request.accept(HELP_ID, FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
      assertThat(request.helps()).singleElement().extracting(Help::isAccepted).isEqualTo(true);
      assertThat(request.pullEvents())
          .containsExactly(
              new HelpAccepted(
                  HELP_REQUEST_ID,
                  LENA,
                  HELP_ID,
                  HelpProviderType.GRANDMA_AVATAR,
                  Optional.empty(),
                  HelpRequestStatus.CLOSED,
                  FRIDAY_18_10.instant()));
    }

    @DisplayName(
        "Jonas and Mia answer; Lena accepts Mia's Help -> CLOSED, only Mia's Help accepted, Jonas's not; HelpAccepted names Mia as helpProvider -- B3-S04 US-CA-03 INV-05 R4.E3")
    @Test
    void lenaAcceptsMiasOfTwoAnswers() {
      HelpRequest request = lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
      request.provideHelp(
          HELP_ID,
          HelpProvider.community(JONAS),
          "Warm the bowl",
          BUTTER_IN_A_THIN_STREAM,
          FRIDAY_18_10);
      request.provideHelp(
          OTHER_HELP_ID,
          HelpProvider.community(MIA),
          "Butter in a thin stream",
          BUTTER_IN_A_THIN_STREAM,
          FRIDAY_18_10);
      request.pullEvents();

      request.accept(OTHER_HELP_ID, FRIDAY_18_10);

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
      assertThat(request.helps())
          .extracting(Help::id, Help::isAccepted)
          .containsExactly(
              org.assertj.core.groups.Tuple.tuple(HELP_ID, false),
              org.assertj.core.groups.Tuple.tuple(OTHER_HELP_ID, true));
      assertThat(request.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              HelpAccepted.class, accepted -> assertThat(accepted.helpProvider()).contains(MIA));
    }

    @DisplayName(
        "Community and Grandma asked; Lena accepts Grandma's Help; Jonas (Community) answers afterwards -> InvalidHelpRequestState(CLOSED), no Help added -- B3-S04 US-CA-03 INV-05 R4.E4")
    @Test
    void acceptingClosesTheRequestForEveryProvider() {
      HelpRequest request =
          lenaAsksAboutStep4(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
      grandmaExplainsStep4(request);
      request.accept(HELP_ID, FRIDAY_18_10);

      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      OTHER_HELP_ID,
                      HelpProvider.community(JONAS),
                      "Warm the bowl",
                      BUTTER_IN_A_THIN_STREAM,
                      FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CLOSED");
      assertThat(request.helps()).extracting(Help::id).containsExactly(HELP_ID);
    }

    @DisplayName(
        "Lena accepts on her OPEN request (no answer yet) -> InvalidHelpRequestState(OPEN), no event -- B3-S04 US-CA-03 lifecycle (accept only from Answered; the lifecycle guard comes before the Help lookup)")
    @Test
    void openRequestCannotBeAccepted() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      assertThatThrownBy(() -> request.accept(HELP_ID, FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("OPEN");
      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Lena accepts a Help id that is not part of her request -> HelpNotInRequest, status stays ANSWERED, no event -- B3-S04 US-CA-03 INV-05 foreign help")
    @Test
    void helpOfAnotherRequestCannotBeAccepted() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      assertThatThrownBy(() -> request.accept(OTHER_HELP_ID, FRIDAY_18_10))
          .isInstanceOfSatisfying(
              HelpNotInRequest.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-05");
                assertThat(rejection.code()).isEqualTo("NOT_FOUND");
              });
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps()).extracting(Help::isAccepted).containsExactly(false);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Lena rejected Grandma's first answer; Grandma answers again; Lena accepts the first answer -> HelpAlreadyRejected (rule INV-39), status stays ANSWERED, no event -- B3-S04 US-CA-03 INV-39 S3.E4")
    @Test
    void rejectedAnswerIsNeverAccepted() {
      HelpRequest request = grandmaAnsweredTwiceAfterARejection();

      assertThatThrownBy(() -> request.accept(HELP_ID, at("2026-11-06T21:00")))
          .isInstanceOfSatisfying(
              HelpAlreadyRejected.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-39");
                assertThat(rejection.code()).isEqualTo("INVALID_STATE");
              });
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps()).extracting(Help::isAccepted).containsExactly(false, false);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "accept on a CANCELLED request -> InvalidHelpRequestState(CANCELLED), no event -- B3-S04 US-CA-03 lifecycle")
    @Test
    void cancelledRequestCannotBeAccepted() {
      HelpRequest cancelled = storedRequestToGrandmaIn(HelpRequestStatus.CANCELLED);

      assertThatThrownBy(() -> cancelled.accept(HELP_ID, FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CANCELLED");
      assertThat(cancelled.pullEvents()).isEmpty();
    }

    @DisplayName(
        "accept on a CLOSED request -> InvalidHelpRequestState(CLOSED), no event -- B3-S04 US-CA-03 lifecycle (step 13, D-1)")
    @Test
    void closedRequestCannotBeAccepted() {
      HelpRequest closed = storedRequestToGrandmaIn(HelpRequestStatus.CLOSED);

      assertThatThrownBy(() -> closed.accept(HELP_ID, FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CLOSED");
      assertThat(closed.pullEvents()).isEmpty();
    }
  }

  @Nested
  class RejectHelp { // B3-S05, US-CA-03

    @DisplayName(
        "reject on a CLOSED request -> InvalidHelpRequestState(CLOSED), no event -- B3-S05 US-CA-03 lifecycle (step 13, D-1)")
    @Test
    void closedRequestCannotBeRejected() {
      HelpRequest closed = storedRequestToGrandmaIn(HelpRequestStatus.CLOSED);

      assertThatThrownBy(() -> closed.reject(HELP_ID, "warm water didn't help", FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CLOSED");
      assertThat(closed.pullEvents()).isEmpty();
    }

    @DisplayName(
        "reject on a CANCELLED request -> InvalidHelpRequestState(CANCELLED), no event -- B3-S05 US-CA-03 lifecycle (step 13, D-1)")
    @Test
    void cancelledRequestCannotBeRejected() {
      HelpRequest cancelled = storedRequestToGrandmaIn(HelpRequestStatus.CANCELLED);

      assertThatThrownBy(() -> cancelled.reject(HELP_ID, "warm water didn't help", FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CANCELLED");
      assertThat(cancelled.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Grandma answered; Fri 20:00 Lena rejects it: 'warm water didn't help' -> OPEN; Grandma's Help marked rejected; Grandma asked again, deadline Sat 08:00, side ASKED; HelpRejected {helpRequest, helpRequester Lena, helpId, note, askedProviders [Grandma, Sat 08:00], OPEN, occurredAt Fri 20:00} -- B3-S05 US-CA-03 INV-06 R5.E1")
    @Test
    void lenaRejectsGrandmasAnswerWithANote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));

      List<AskedProvider> askedAgain =
          List.of(
              new AskedProvider(HelpProviderType.GRANDMA_AVATAR, at("2026-11-07T08:00").instant()));
      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.helps()).singleElement().extracting(Help::isRejected).isEqualTo(true);
      assertThat(request.askedProviders()).isEqualTo(askedAgain);
      assertThat(request.pullEvents())
          .containsExactly(
              new HelpRejected(
                  HELP_REQUEST_ID,
                  LENA,
                  HELP_ID,
                  "warm water didn't help",
                  askedAgain,
                  HelpRequestStatus.OPEN,
                  at("2026-11-06T20:00").instant()));
    }

    @DisplayName(
        "Community and Grandma asked; Jonas answered, nothing accepted; Fri 20:00 Lena rejects Jonas's answer -> both asked again, both deadlines Sat 08:00, both sides ASKED -- B3-S05 US-CA-03 INV-06 R5.E2")
    @Test
    void bothProvidersAreAskedAgain() {
      HelpRequest request =
          lenaAsksAboutStep4(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
      request.provideHelp(
          HELP_ID,
          HelpProvider.community(JONAS),
          "Warm the bowl",
          BUTTER_IN_A_THIN_STREAM,
          FRIDAY_18_10);

      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));

      Instant saturday0800 = at("2026-11-07T08:00").instant();
      assertThat(request.askedProviders())
          .extracting(AskedProvider::providerType, AskedProvider::deadline, AskedProvider::state)
          .containsExactlyInAnyOrder(
              org.assertj.core.groups.Tuple.tuple(
                  HelpProviderType.COMMUNITY, saturday0800, ProviderState.ASKED),
              org.assertj.core.groups.Tuple.tuple(
                  HelpProviderType.GRANDMA_AVATAR, saturday0800, ProviderState.ASKED));
    }

    @DisplayName(
        "Lena rejects Grandma's answer with a blank note -> RejectionNoteRequired (rule INV-06), status stays ANSWERED, no event -- B3-S05 US-CA-03 INV-06 (step 3 r5)")
    @Test
    void rejectionNeedsANote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      assertThatThrownBy(() -> request.reject(HELP_ID, "   ", at("2026-11-06T20:00")))
          .isInstanceOfSatisfying(
              RejectionNoteRequired.class,
              rejection -> {
                assertThat(rejection.rule()).isEqualTo("INV-06");
                assertThat(rejection.code()).isEqualTo("MISSING_FIELD");
              });
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps()).extracting(Help::isRejected).containsExactly(false);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "reject on an OPEN request -> InvalidHelpRequestState(OPEN), no event -- B3-S05 US-CA-03 lifecycle (reject only from Answered)")
    @Test
    void openRequestCannotBeRejected() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      assertThatThrownBy(
              () -> request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00")))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("OPEN");
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Lena rejects a Help id that is not part of her request -> HelpNotInRequest, no event -- B3-S05 US-CA-03 INV-06")
    @Test
    void helpOfAnotherRequestCannotBeRejected() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      assertThatThrownBy(
              () -> request.reject(OTHER_HELP_ID, "warm water didn't help", at("2026-11-06T20:00")))
          .isInstanceOf(HelpNotInRequest.class);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Lena rejected Grandma's first answer; Grandma answers again; Lena rejects the first answer again -> HelpAlreadyRejected (rule INV-39), no event -- B3-S05 US-CA-03 INV-39")
    @Test
    void rejectedAnswerCannotBeRejectedAgain() {
      HelpRequest request = grandmaAnsweredTwiceAfterARejection();

      assertThatThrownBy(() -> request.reject(HELP_ID, "still splits", at("2026-11-06T21:00")))
          .isInstanceOf(HelpAlreadyRejected.class);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.pullEvents()).isEmpty();
    }
  }

  @Nested
  class TimeOutHelpRequest { // B3-S06, US-CA-03

    @DisplayName(
        "only Community asked Fri 18:00, silent; time out at Sat 06:00:01 -> CANCELLED, Community's side TIMED_OUT; HelpRequestTimedOut {helpRequest, helpRequester Lena, timedOutProviders [COMMUNITY], suggestedProviders [GRANDMA_AVATAR], CANCELLED, occurredAt Sat 06:00:01} -- B3-S06 US-CA-03 INV-09 R8.E1")
    @Test
    void silentCommunityTimesOutAndGrandmaIsSuggested() {
      HelpRequest request = lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
      request.pullEvents();

      request.timeOut(at("2026-11-07T06:00:01"));

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.askedProviders())
          .singleElement()
          .extracting(AskedProvider::state)
          .isEqualTo(ProviderState.TIMED_OUT);
      assertThat(request.pullEvents())
          .containsExactly(
              new HelpRequestTimedOut(
                  HELP_REQUEST_ID,
                  LENA,
                  List.of(HelpProviderType.COMMUNITY),
                  List.of(HelpProviderType.GRANDMA_AVATAR),
                  HelpRequestStatus.CANCELLED,
                  at("2026-11-07T06:00:01").instant()));
    }

    @DisplayName(
        "only Grandma asked Fri 18:00, silent; time out at Sat 06:00:01 -> CANCELLED; timedOutProviders [GRANDMA_AVATAR], suggestedProviders [COMMUNITY] -- B3-S06 US-CA-03 INV-09 R8.E2")
    @Test
    void silentGrandmaTimesOutAndCommunityIsSuggested() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      request.timeOut(at("2026-11-07T06:00:01"));

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              HelpRequestTimedOut.class,
              timedOut -> {
                assertThat(timedOut.timedOutProviders())
                    .containsExactly(HelpProviderType.GRANDMA_AVATAR);
                assertThat(timedOut.suggestedProviders())
                    .containsExactly(HelpProviderType.COMMUNITY);
              });
    }

    @DisplayName(
        "Community and Grandma asked, both silent 12 h; time out at Sat 06:00:01 -> CANCELLED; timedOutProviders [COMMUNITY, GRANDMA_AVATAR], suggestedProviders [] (Lena is told by the notification policy) -- B3-S06 US-CA-03 INV-09 R8.E3")
    @Test
    void bothSilentTimeOutWithNothingToSuggest() {
      HelpRequest request =
          lenaAsksAboutStep4(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
      request.pullEvents();

      request.timeOut(at("2026-11-07T06:00:01"));

      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.askedProviders())
          .extracting(AskedProvider::state)
          .containsOnly(ProviderState.TIMED_OUT);
      assertThat(request.pullEvents())
          .singleElement()
          .isInstanceOfSatisfying(
              HelpRequestTimedOut.class,
              timedOut -> {
                assertThat(timedOut.timedOutProviders())
                    .containsExactlyInAnyOrder(
                        HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
                assertThat(timedOut.suggestedProviders()).isEmpty();
              });
    }

    @DisplayName(
        "time out at Sat 06:00:00, exactly the deadline -> DeadlineNotPassed, status stays OPEN, no event -- B3-S06 US-CA-03 INV-09 boundary (step 3 r5)")
    @Test
    void timeOutAtTheDeadlineIsTooEarly() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.pullEvents();

      assertThatThrownBy(() -> request.timeOut(at("2026-11-07T06:00:00")))
          .isInstanceOfSatisfying(
              DeadlineNotPassed.class,
              rejection -> assertThat(rejection.rule()).isEqualTo("INV-09"));
      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
      assertThat(request.askedProviders())
          .extracting(AskedProvider::state)
          .containsOnly(ProviderState.ASKED);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "Grandma answered, Lena rejected Fri 20:00 (new deadline Sat 08:00); time out at Sat 06:00:01 -> DeadlineNotPassed -- B3-S06 US-CA-03 INV-06,INV-09")
    @Test
    void rejectionStartsANewDeadline() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));

      assertThatThrownBy(() -> request.timeOut(at("2026-11-07T06:00:01")))
          .isInstanceOf(DeadlineNotPassed.class);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.OPEN);
    }

    @DisplayName(
        "Grandma answered; time out at Sat 06:00:01 -> InvalidHelpRequestState(ANSWERED), no event (only Open times out, D5) -- B3-S06 US-CA-03 lifecycle (step 3 r5)")
    @Test
    void answeredRequestDoesNotTimeOut() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);
      request.pullEvents();

      assertThatThrownBy(() -> request.timeOut(at("2026-11-07T06:00:01")))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("ANSWERED");
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "time out a CANCELLED request -> InvalidHelpRequestState(CANCELLED), no event -- B3-S06 US-CA-03 lifecycle")
    @Test
    void cancelledRequestDoesNotTimeOut() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      request.cancel(Optional.empty(), FRIDAY_18_10);
      request.pullEvents();

      assertThatThrownBy(() -> request.timeOut(at("2026-11-07T06:00:01")))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CANCELLED");
      assertThat(request.status()).isEqualTo(HelpRequestStatus.CANCELLED);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "time out a CLOSED request -> InvalidHelpRequestState(CLOSED), no event -- B3-S06 US-CA-03 lifecycle (step 13, D-1)")
    @Test
    void closedRequestDoesNotTimeOut() {
      HelpRequest closed = storedRequestToGrandmaIn(HelpRequestStatus.CLOSED);

      assertThatThrownBy(() -> closed.timeOut(at("2026-11-07T06:00:01")))
          .isInstanceOf(InvalidHelpRequestState.class)
          .hasMessageContaining("CLOSED");
      assertThat(closed.status()).isEqualTo(HelpRequestStatus.CLOSED);
      assertThat(closed.pullEvents()).isEmpty();
    }

    @DisplayName(
        "D12 suggestions after a time-out: asked [COMMUNITY TIMED_OUT] -> [GRANDMA_AVATAR]; asked [COMMUNITY ASKED] -> []; asked [COMMUNITY TIMED_OUT, GRANDMA_AVATAR TIMED_OUT] -> [] -- B3-S06 US-CA-03 INV-09 R8.E1 op getHelpRequestById suggestedProviders")
    @Test
    void suggestionsFollowTheTimedOutProviders() {
      Instant deadline = at("2026-11-07T06:00").instant();

      assertThat(
              HelpRequest.suggestionsAfterTimeOut(
                  List.of(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY, deadline, ProviderState.TIMED_OUT))))
          .containsExactly(HelpProviderType.GRANDMA_AVATAR);
      assertThat(
              HelpRequest.suggestionsAfterTimeOut(
                  List.of(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY, deadline, ProviderState.ASKED))))
          .isEmpty();
      assertThat(
              HelpRequest.suggestionsAfterTimeOut(
                  List.of(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY, deadline, ProviderState.TIMED_OUT),
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR, deadline, ProviderState.TIMED_OUT))))
          .isEmpty();
    }
  }

  @Nested
  class Notes { // B3-S03, B3-S05, US-CA-03 (step 6 r1 of increment 1, from step 11, ADR0016 L6)

    @DisplayName(
        "D1 a new request (Lena asks Grandma about step 4) has no notes -- B3-S01 US-CA-03 INV-07")
    @Test
    void aNewRequestHasNoNotes() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThat(request.notes()).isEmpty();
    }

    @DisplayName(
        "D2 Lena cancels at Fri 18:10 with 'an ice cube saved it' -> notes [CANCELLATION 'an ice cube saved it' Fri 18:10] -- B3-S03 US-CA-03 INV-08 R7.E1, INV-07")
    @Test
    void cancellationWritesItsNote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      request.cancel(Optional.of("an ice cube saved it"), FRIDAY_18_10);

      assertThat(request.notes())
          .containsExactly(
              new Note(
                  NoteKind.CANCELLATION,
                  "an ice cube saved it",
                  FRIDAY_18_10.instant(),
                  Optional.empty()));
    }

    @DisplayName("D3 Lena cancels without a note -> no notes -- B3-S03 US-CA-03 INV-08 R7.E2")
    @Test
    void cancellationWithoutANoteWritesNone() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      request.cancel(Optional.empty(), FRIDAY_18_10);

      assertThat(request.notes()).isEmpty();
    }

    @DisplayName(
        "D4 Lena rejects Grandma's answer at Fri 20:00: 'warm water didn't help' -> notes [REJECTION 'warm water didn't help' Fri 20:00] -- B3-S05 US-CA-03 INV-06 R5.E1, INV-07 R6.E1")
    @Test
    void rejectionWritesItsNote() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);

      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));

      assertThat(request.notes())
          .containsExactly(
              new Note(
                  NoteKind.REJECTION,
                  "warm water didn't help",
                  at("2026-11-06T20:00").instant(),
                  Optional.of(HELP_ID)));
    }

    @DisplayName(
        "D5 rejected at Fri 20:00 ('warm water didn't help'), cancelled at Fri 21:00 ('an ice cube saved it') -> notes [REJECTION Fri 20:00, CANCELLATION Fri 21:00], in the order written -- B3-S03,B3-S05 US-CA-03 INV-07")
    @Test
    void notesKeepTheOrderTheyWereWritten() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);

      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));
      request.cancel(Optional.of("an ice cube saved it"), at("2026-11-06T21:00"));

      assertThat(request.notes())
          .containsExactly(
              new Note(
                  NoteKind.REJECTION,
                  "warm water didn't help",
                  at("2026-11-06T20:00").instant(),
                  Optional.of(HELP_ID)),
              new Note(
                  NoteKind.CANCELLATION,
                  "an ice cube saved it",
                  at("2026-11-06T21:00").instant(),
                  Optional.empty()));
    }

    @DisplayName(
        "D6 Lena cancels a CLOSED request with a note -> InvalidHelpRequestState; notes stay empty -- B3-S03 US-CA-03 INV-08")
    @Test
    void refusedCancellationWritesNoNote() {
      HelpRequest request = storedRequestToGrandmaIn(HelpRequestStatus.CLOSED);

      assertThatThrownBy(() -> request.cancel(Optional.of("an ice cube saved it"), FRIDAY_18_10))
          .isInstanceOf(InvalidHelpRequestState.class);
      assertThat(request.notes()).isEmpty();
    }

    @DisplayName(
        "D7 Lena rejects the already rejected Help again with 'still splits' -> HelpAlreadyRejected; notes stay [REJECTION 'warm water didn't help'] -- B3-S05 US-CA-03 INV-39")
    @Test
    void refusedRejectionWritesNoNote() {
      HelpRequest request = grandmaAnsweredTwiceAfterARejection();

      assertThatThrownBy(() -> request.reject(HELP_ID, "still splits", at("2026-11-06T21:00")))
          .isInstanceOf(HelpAlreadyRejected.class);
      assertThat(request.notes())
          .containsExactly(
              new Note(
                  NoteKind.REJECTION,
                  "warm water didn't help",
                  at("2026-11-06T20:00").instant(),
                  Optional.of(HELP_ID)));
    }

    @DisplayName(
        "D8 reconstituting an OPEN request with notes [REJECTION 'warm water didn't help' (HELP_ID) Fri 20:00] restores the notes and records no event -- B3-S05 US-CA-03 INV-07 reconstitution")
    @Test
    void reconstitutionRestoresTheNotes() {
      Note warmWater =
          new Note(
              NoteKind.REJECTION,
              "warm water didn't help",
              at("2026-11-06T20:00").instant(),
              Optional.of(HELP_ID));

      HelpRequest request =
          HelpRequest.reconstitute(
              HELP_REQUEST_ID,
              LENA,
              "How do I keep the sauce from splitting?",
              "Step 4 of my Hollandaise keeps splitting",
              HelpType.PREPARATION_STEP_EXPLANATION,
              HOLLANDAISE,
              STEP_4,
              List.of(
                  new AskedProvider(
                      HelpProviderType.GRANDMA_AVATAR, at("2026-11-07T08:00").instant())),
              HelpRequestStatus.OPEN,
              List.of(),
              List.of(warmWater),
              2L);

      assertThat(request.notes()).containsExactly(warmWater);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "D9 a rejection note names the rejected Help (HELP_ID); a cancellation note names none -- B3-S05,B3-S03 US-CA-03 INV-06 op getHelpOfHelpRequest rejectionNote")
    @Test
    void rejectionNoteNamesTheRejectedHelp() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();
      grandmaExplainsStep4(request);

      request.reject(HELP_ID, "warm water didn't help", at("2026-11-06T20:00"));
      request.cancel(Optional.of("an ice cube saved it"), at("2026-11-06T21:00"));

      assertThat(request.notes())
          .extracting(Note::kind, Note::help)
          .containsExactly(
              tuple(NoteKind.REJECTION, Optional.of(HELP_ID)),
              tuple(NoteKind.CANCELLATION, Optional.empty()));
    }

    @DisplayName(
        "D10 a REJECTION note without a Help -> IllegalArgumentException 'a rejection note names the rejected Help' -- B3-S05 US-CA-03 INV-06 note shape")
    @Test
    void rejectionNoteWithoutAHelpIsRefused() {
      assertThatThrownBy(
              () ->
                  new Note(
                      NoteKind.REJECTION,
                      "warm water didn't help",
                      at("2026-11-06T20:00").instant(),
                      Optional.empty()))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("a rejection note names the rejected Help");
    }

    @DisplayName(
        "D11 a CANCELLATION note naming a Help -> IllegalArgumentException 'a cancellation note names no Help' -- B3-S03 US-CA-03 INV-08 note shape")
    @Test
    void cancellationNoteWithAHelpIsRefused() {
      assertThatThrownBy(
              () ->
                  new Note(
                      NoteKind.CANCELLATION,
                      "an ice cube saved it",
                      at("2026-11-06T21:00").instant(),
                      Optional.of(HELP_ID)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("a cancellation note names no Help");
    }
  }

  @Nested
  class Lifecycle { // reconstitution and events, B3-S01 + B3-S02

    @DisplayName(
        "pulling events returns the recorded events once; a second pull returns none -- B3-S01 US-CA-03 aggregate shape")
    @Test
    void pulledEventsAreReturnedOnlyOnce() {
      HelpRequest request = lenaAsksGrandmaAboutStep4();

      assertThat(request.pullEvents()).hasSize(1);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "reconstituting an ANSWERED request with one Help and version 3 restores its state and version and records no event -- B3-S01 US-CA-03 reconstitution")
    @Test
    void reconstitutionRestoresStateWithoutEvents() {
      Instant saturday0600 = Instant.parse("2026-11-07T05:00:00Z");
      AskedProvider grandmaAnswered =
          new AskedProvider(HelpProviderType.GRANDMA_AVATAR, saturday0600, ProviderState.ANSWERED);
      Help grandmasHelp =
          new Help(
              HELP_ID,
              HelpProvider.grandmaAvatar(),
              "Butter in a thin stream",
              BUTTER_IN_A_THIN_STREAM);

      HelpRequest request =
          HelpRequest.reconstitute(
              HELP_REQUEST_ID,
              LENA,
              "How do I keep the sauce from splitting?",
              "Step 4 of my Hollandaise keeps splitting",
              HelpType.PREPARATION_STEP_EXPLANATION,
              HOLLANDAISE,
              STEP_4,
              List.of(grandmaAnswered),
              HelpRequestStatus.ANSWERED,
              List.of(grandmasHelp),
              List.of(),
              3L);

      assertThat(request.id()).isEqualTo(HELP_REQUEST_ID);
      assertThat(request.requester()).isEqualTo(LENA);
      assertThat(request.title()).isEqualTo("How do I keep the sauce from splitting?");
      assertThat(request.description()).isEqualTo("Step 4 of my Hollandaise keeps splitting");
      assertThat(request.type()).isEqualTo(HelpType.PREPARATION_STEP_EXPLANATION);
      assertThat(request.recipe()).isEqualTo(HOLLANDAISE);
      assertThat(request.howToStep()).isEqualTo(STEP_4);
      assertThat(request.askedProviders()).containsExactly(grandmaAnswered);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.ANSWERED);
      assertThat(request.helps()).extracting(Help::id).containsExactly(HELP_ID);
      assertThat(request.version()).isEqualTo(3L);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "a Help is reconstituted from stored state (id, provider Jonas, answer title, answer, accepted) without guards -- B3-S02 US-CA-03 reconstitution (step 6 r1, from step 7)")
    @Test
    void helpIsReconstitutedFromStoredState() {
      Help help =
          Help.reconstitute(
              HELP_ID,
              HelpProvider.community(JONAS),
              "Butter in a thin stream",
              BUTTER_IN_A_THIN_STREAM,
              true);

      assertThat(help.id()).isEqualTo(HELP_ID);
      assertThat(help.providerType()).isEqualTo(HelpProviderType.COMMUNITY);
      assertThat(help.provider()).contains(JONAS);
      assertThat(help.answerTitle()).isEqualTo("Butter in a thin stream");
      assertThat(help.answer()).isEqualTo(BUTTER_IN_A_THIN_STREAM);
      assertThat(help.isAccepted()).isTrue();
    }

    @DisplayName(
        "reconstituting a stored request asked of a Chef (state not reachable through commands) succeeds without running guards -- B3-S01 US-CA-03 reconstitution, no guards")
    @Test
    void reconstitutionRunsNoGuards() {
      HelpRequest request = storedRequestIn(HelpRequestStatus.OPEN, HelpProviderType.CHEF);

      assertThat(request.askedProviderTypes()).containsExactly(HelpProviderType.CHEF);
      assertThat(request.pullEvents()).isEmpty();
    }

    @DisplayName(
        "a request is reconstituted with a rejected Help, an accepted Help and a TIMED_OUT side -> the marks and side states are kept, no guards, no events -- B3-S04,B3-S05,B3-S06 US-CA-03 reconstitution")
    @Test
    void reconstitutionKeepsMarksAndSides() {
      Instant saturday0600 = Instant.parse("2026-11-07T05:00:00Z");
      AskedProvider communityTimedOut =
          new AskedProvider(HelpProviderType.COMMUNITY, saturday0600, ProviderState.TIMED_OUT);
      AskedProvider grandmaAnswered =
          new AskedProvider(HelpProviderType.GRANDMA_AVATAR, saturday0600, ProviderState.ANSWERED);
      Help rejectedHelp =
          Help.reconstitute(
              HELP_ID,
              HelpProvider.grandmaAvatar(),
              "Butter in a thin stream",
              BUTTER_IN_A_THIN_STREAM,
              false,
              true);
      Help acceptedHelp =
          Help.reconstitute(
              OTHER_HELP_ID,
              HelpProvider.grandmaAvatar(),
              "Warm the butter first",
              BUTTER_IN_A_THIN_STREAM,
              true,
              false);

      HelpRequest request =
          HelpRequest.reconstitute(
              HELP_REQUEST_ID,
              LENA,
              "How do I keep the sauce from splitting?",
              "Step 4 of my Hollandaise keeps splitting",
              HelpType.PREPARATION_STEP_EXPLANATION,
              HOLLANDAISE,
              STEP_4,
              List.of(communityTimedOut, grandmaAnswered),
              HelpRequestStatus.CLOSED,
              List.of(rejectedHelp, acceptedHelp),
              List.of(),
              5L);

      assertThat(request.helps())
          .extracting(Help::id, Help::isAccepted, Help::isRejected)
          .containsExactly(tuple(HELP_ID, false, true), tuple(OTHER_HELP_ID, true, false));
      assertThat(request.askedProviders())
          .extracting(AskedProvider::state)
          .containsExactly(ProviderState.TIMED_OUT, ProviderState.ANSWERED);
      assertThat(request.status()).isEqualTo(HelpRequestStatus.CLOSED);
      assertThat(request.pullEvents()).isEmpty();
    }
  }
}
