package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.AnswerDoesNotMatchRequest;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvided;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.domain.helprequest.NoSubstituteProposed;
import org.larder.cookingassistance.domain.helprequest.OwnRequest;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.ProviderNotAsked;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteRejectedBefore;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.domain.helprequest.TooManyPictures;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Provide Help through the REST API (B3-S02, US-CA-03): a Community member answers. Access
 * per ADR0013: role community. One retry on ConcurrencyConflict (ADR0010).
 */
class ProvideHelpTest {

  static final Actor JONAS_AS_COMMUNITY =
      new Actor(HelpRequests.JONAS, Set.of(Role.COOK, Role.COMMUNITY), false);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final ProvideHelp useCase =
      new ProvideHelp(helpRequests, () -> HelpRequests.HELP_ID.value(), HelpRequests.FRIDAY_18_10);

  /** One hour after Lena asked about the creme fraiche. */
  static final Clock MONDAY_19_00 = Clock.offset(HelpRequests.MONDAY_18_00, Duration.ofHours(1));

  final ProvideHelp ingredientUseCase =
      new ProvideHelp(helpRequests, () -> HelpRequests.OTHER_HELP_ID.value(), MONDAY_19_00);

  @BeforeEach
  void lenasRequestToTheCommunityIsStored() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
  }

  static ProvideHelp.Command answerFrom(Actor actor) {
    return new ProvideHelp.Command(
        actor,
        HelpProviderType.COMMUNITY,
        HelpRequests.HELP_REQUEST_ID,
        "Warm the bowl",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS);
  }

  @DisplayName(
      "Jonas (role community) answers Lena's stored request to the Community -> the help id from the id generator is returned; the saved request holds Jonas's Help -- B3-S02 US-CA-03 cmd Provide Help")
  @Test
  void communityMemberProvidesHelpAndGetsTheNewHelpId() {
    HelpId helpId = useCase.execute(answerFrom(JONAS_AS_COMMUNITY));

    assertThat(helpId).isEqualTo(HelpRequests.HELP_ID);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(help -> assertThat(help.provider()).contains(HelpRequests.JONAS));
  }

  @DisplayName(
      "the HelpProvided event is saved with the request -- B3-S02 US-CA-03 evt HelpProvided")
  @Test
  void helpProvidedIsSavedWithTheRequest() {
    useCase.execute(answerFrom(JONAS_AS_COMMUNITY));

    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpProvided.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpProvided.class,
            event -> assertThat(event.helpId()).isEqualTo(HelpRequests.HELP_ID));
  }

  @DisplayName(
      "unknown help request id -> HelpRequestNotFound, nothing saved -- B3-S02 US-CA-03 404")
  @Test
  void unknownHelpRequestIsNotFound() {
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ProvideHelp.Command(
                        JONAS_AS_COMMUNITY,
                        HelpProviderType.COMMUNITY,
                        unknown,
                        "Warm the bowl",
                        HelpRequests.BUTTER_WITH_THREE_PHOTOS)))
        .isInstanceOf(HelpRequestNotFound.class);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpProvided.class::isInstance);
  }

  @DisplayName(
      "Lena answers her own request (she holds role community) -> OwnRequest passes through unchanged (rule INV-36), nothing saved -- B3-S02 US-CA-03 INV-36")
  @Test
  void ownRequestPassesThrough() {
    Actor lenaAsCommunity = new Actor(HelpRequests.LENA, Set.of(Role.COOK, Role.COMMUNITY), false);

    assertThatThrownBy(() -> useCase.execute(answerFrom(lenaAsCommunity)))
        .isInstanceOfSatisfying(
            OwnRequest.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-36"));
    assertNoHelpSaved();
  }

  private void assertNoHelpSaved() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpProvided.class::isInstance);
  }

  @DisplayName(
      "an answer about step 6 on a step-4 request -> AnswerDoesNotMatchRequest passes through unchanged (rule INV-37) -- B3-S02 US-CA-03 INV-37")
  @Test
  void answerDoesNotMatchRequestPassesThrough() {
    HowToStepId step6 = new HowToStepId(UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-2a3b4c5d6e7f"));
    PreparationStepExplanation aboutStep6 =
        new PreparationStepExplanation(
            HelpRequests.HOLLANDAISE, step6, "Warm the butter to 60 degrees", List.of());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ProvideHelp.Command(
                        JONAS_AS_COMMUNITY,
                        HelpProviderType.COMMUNITY,
                        HelpRequests.HELP_REQUEST_ID,
                        "About step 6",
                        aboutStep6)))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-37"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "11 pictures -> TooManyPictures passes through unchanged (rule INV-38) -- B3-S02 US-CA-03 INV-38")
  @Test
  void tooManyPicturesPassesThrough() {
    List<URI> elevenPhotos =
        IntStream.rangeClosed(1, 11)
            .mapToObj(i -> URI.create("https://larder.org/media/images/photo-" + i))
            .toList();
    PreparationStepExplanation withElevenPhotos =
        new PreparationStepExplanation(
            HelpRequests.HOLLANDAISE, HelpRequests.STEP_4, "Add the butter slowly", elevenPhotos);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ProvideHelp.Command(
                        JONAS_AS_COMMUNITY,
                        HelpProviderType.COMMUNITY,
                        HelpRequests.HELP_REQUEST_ID,
                        "Butter",
                        withElevenPhotos)))
        .isInstanceOfSatisfying(
            TooManyPictures.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-38"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "request sent only to Grandma Avatar; Jonas answers -> ProviderNotAsked passes through unchanged -- B3-S02 US-CA-03 INV-01")
  @Test
  void providerNotAskedPassesThrough() {
    InMemoryHelpRequestRepository onlyGrandmaAsked = new InMemoryHelpRequestRepository();
    onlyGrandmaAsked.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    ProvideHelp provideHelp =
        new ProvideHelp(
            onlyGrandmaAsked, () -> HelpRequests.HELP_ID.value(), HelpRequests.FRIDAY_18_10);

    assertThatThrownBy(() -> provideHelp.execute(answerFrom(JONAS_AS_COMMUNITY)))
        .isInstanceOf(ProviderNotAsked.class);
    assertThat(onlyGrandmaAsked.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .isEmpty();
  }

  @DisplayName(
      "a CANCELLED request -> InvalidHelpRequestState passes through unchanged -- B3-S02 US-CA-03 lifecycle")
  @Test
  void invalidStatePassesThrough() {
    InMemoryHelpRequestRepository withCancelledRequest = new InMemoryHelpRequestRepository();
    HelpRequest open = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    withCancelledRequest.save(
        HelpRequest.reconstitute(
            open.id(),
            open.requester(),
            open.title(),
            open.description(),
            open.type(),
            open.recipe(),
            open.howToStep(),
            open.askedProviders(),
            HelpRequestStatus.CANCELLED,
            List.of(),
            List.of(),
            0L));
    ProvideHelp provideHelp =
        new ProvideHelp(
            withCancelledRequest, () -> HelpRequests.HELP_ID.value(), HelpRequests.FRIDAY_18_10);

    assertThatThrownBy(() -> provideHelp.execute(answerFrom(JONAS_AS_COMMUNITY)))
        .isInstanceOf(InvalidHelpRequestState.class);
    assertThat(withCancelledRequest.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .isEmpty();
  }

  @DisplayName(
      "another writer saves first (Mia answers in between) -> the use case reloads and retries once: both Jonas's and Mia's Helps are stored -- B3-S02 US-CA-03 ADR0010 QS-10")
  @Test
  void conflictIsRetriedOnce() {
    helpRequests.interleave(this::miaAnswersMeanwhile);

    useCase.execute(answerFrom(JONAS_AS_COMMUNITY));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .extracting(help -> help.provider().orElseThrow())
        .containsExactlyInAnyOrder(HelpRequests.MIA, HelpRequests.JONAS);
  }

  /** Another writer: Mia answers the same request and saves first. */
  private void miaAnswersMeanwhile() {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(
        meanwhile, HelpRequests.OTHER_HELP_ID, HelpRequests.MIA);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "the retry conflicts too -> ConcurrencyConflict propagates; only the other writers' changes are stored -- B3-S02 US-CA-03 ADR0010")
  @Test
  void secondConflictPropagates() {
    helpRequests.interleave(
        () -> {
          miaAnswersMeanwhile();
          helpRequests.interleave(this::samAnswersMeanwhile);
        });

    assertThatThrownBy(() -> useCase.execute(answerFrom(JONAS_AS_COMMUNITY)))
        .isInstanceOf(ConcurrencyConflict.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .extracting(help -> help.provider().orElseThrow())
        .containsExactlyInAnyOrder(HelpRequests.MIA, SAM);
  }

  static final CookId SAM = new CookId(UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a81"));

  /** A third writer: Community member Sam answers and saves first during the retry. */
  private void samAnswersMeanwhile() {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(
        meanwhile, new HelpId(UUID.fromString("6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b92")), SAM);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "an actor without role community (only cook) -> NotAuthorized before the request is loaded, nothing saved -- B3-S02 US-CA-03 ADR0013")
  @Test
  void actorWithoutCommunityRoleIsRefused() {
    Actor cookOnly = new Actor(HelpRequests.JONAS, Set.of(Role.COOK), false);
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new ProvideHelp.Command(
                        cookOnly,
                        HelpProviderType.COMMUNITY,
                        unknown,
                        "Warm the bowl",
                        HelpRequests.BUTTER_WITH_THREE_PHOTOS)))
        .isInstanceOf(NotAuthorized.class);
    assertNoHelpSaved();
  }

  @DisplayName(
      "Jonas (role community) answers stating provider type GRANDMA_AVATAR -> NotAuthorized before the request is loaded, nothing saved -- B3-S02 US-CA-03 ADR0013")
  @Test
  void communityMemberCannotAnswerAsGrandmaAvatar() {
    assertThatThrownBy(
            () -> useCase.execute(answerAs(JONAS_AS_COMMUNITY, HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOf(NotAuthorized.class);
    assertNoHelpSaved();
  }

  static ProvideHelp.Command answerAs(Actor actor, HelpProviderType providerType) {
    return new ProvideHelp.Command(
        actor,
        providerType,
        HelpRequests.HELP_REQUEST_ID,
        "Warm the bowl",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS);
  }

  @DisplayName(
      "an actor with roles cook, community and chef answers stating provider type CHEF -> NotAuthorized until increment 4 builds chef answers, nothing saved -- B3-S02 US-CA-03 ADR0013")
  @Test
  void chefAnswersAreNotYetAuthorized() {
    Actor jonasAsChefToo =
        new Actor(HelpRequests.JONAS, Set.of(Role.COOK, Role.COMMUNITY, Role.CHEF), false);

    assertThatThrownBy(() -> useCase.execute(answerAs(jonasAsChefToo, HelpProviderType.CHEF)))
        .isInstanceOf(NotAuthorized.class);
    assertNoHelpSaved();
  }

  @DisplayName(
      "Jonas (role community) proposes yogurt on Lena's ingredient request -> the help id is returned; the saved Help holds the Substitutes; HelpProvided is saved with it -- B2-S02 US-CA-02 cmd Provide Help")
  @Test
  void communityMemberProposesSubstitutes() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));
    Substitutes yogurt =
        new Substitutes(HelpRequests.TOMATO_SOUP, List.of(HelpRequests.gramsOf("yogurt", "200")));

    HelpId id = ingredientUseCase.execute(jonasProposes(yogurt));

    assertThat(id).isEqualTo(HelpRequests.OTHER_HELP_ID);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            saved ->
                assertThat(saved.helps())
                    .singleElement()
                    .extracting(Help::answer)
                    .isEqualTo(yogurt));
    assertThat(helpRequests.recordedEvents()).last().isInstanceOf(HelpProvided.class);
  }

  @DisplayName(
      "an answer with no substitute -> NoSubstituteProposed passes through unchanged (rule INV-12), no Help saved -- B2-S02 US-CA-02 INV-12")
  @Test
  void noSubstituteProposedPassesThrough() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));

    assertThatThrownBy(
            () ->
                ingredientUseCase.execute(
                    jonasProposes(new Substitutes(HelpRequests.TOMATO_SOUP, List.of()))))
        .isInstanceOfSatisfying(
            NoSubstituteProposed.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-12"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "substitutes for another ingredient -> AnswerDoesNotMatchRequest passes through unchanged (rule INV-37), no Help saved -- B2-S02 US-CA-02 INV-37")
  @Test
  void substitutesForAnotherIngredientPassThrough() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));
    Substitute forBacon =
        new Substitute(
            new IngredientId(UUID.fromString("5b1f0a2e-7c3d-4e8f-9a6b-1d2c3e4f5a6b")),
            HelpRequests.gramsOf("yogurt", "200").substituteIngredients());

    assertThatThrownBy(
            () ->
                ingredientUseCase.execute(
                    jonasProposes(new Substitutes(HelpRequests.TOMATO_SOUP, List.of(forBacon)))))
        .isInstanceOfSatisfying(
            AnswerDoesNotMatchRequest.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-37"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "Lena rejected yogurt; Jonas proposes yogurt -> SubstituteRejectedBefore passes through unchanged (rule INV-15), no further Help saved -- B3-S05 US-CA-02 INV-15")
  @Test
  void substituteRejectedBeforePassesThrough() {
    helpRequests.clear();
    HelpRequest request =
        HelpRequests.lenaAsksForASubstitute(
            HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, HelpRequests.gramsOf("yogurt", "200"));
    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_19_00);
    helpRequests.save(request);

    assertThatThrownBy(
            () ->
                ingredientUseCase.execute(
                    jonasProposes(
                        new Substitutes(
                            HelpRequests.TOMATO_SOUP,
                            List.of(HelpRequests.gramsOf("yogurt", "200"))))))
        .isInstanceOfSatisfying(
            SubstituteRejectedBefore.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-15"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
  }

  private static ProvideHelp.Command jonasProposes(Substitutes answer) {
    return new ProvideHelp.Command(
        JONAS_AS_COMMUNITY,
        HelpProviderType.COMMUNITY,
        HelpRequests.HELP_REQUEST_ID,
        "Yogurt",
        answer);
  }
}
