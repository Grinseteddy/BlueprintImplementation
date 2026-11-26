package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.ChefNotAllowed;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequested;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.NoHelpProviderChosen;
import org.larder.cookingassistance.domain.helprequest.PictureLinkNotSecure;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.domain.helprequest.TooManyPictures;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Request help Catastrophe (B4-S01, US-CA-04) with contract-checked fakes, a fixed clock
 * (Sat 18:40) and a predictable id generator. Access per ADR0013: role cook; every cook can ask
 * (INV-18).
 */
class RequestHelpWithCatastropheTest {

  static final Actor LENA_AS_COOK = new Actor(HelpRequests.LENA, Set.of(Role.COOK), true);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final RequestHelpWithCatastrophe useCase =
      new RequestHelpWithCatastrophe(
          helpRequests, () -> HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.SATURDAY_18_40);

  static RequestHelpWithCatastrophe.Command lenaAsksAboutBurntChicken(
      Actor actor, HelpProviderType... providers) {
    return lenaAsks(
        actor,
        Optional.of(HelpRequests.ROAST_CHICKEN),
        HelpRequests.BURNT_CHICKEN_PHOTOS,
        providers);
  }

  static RequestHelpWithCatastrophe.Command lenaAsks(
      Actor actor, Optional<RecipeId> recipe, List<URI> pictures, HelpProviderType... providers) {
    return new RequestHelpWithCatastrophe.Command(
        actor,
        "My roast chicken is burnt",
        "The skin is black on one side",
        recipe,
        pictures,
        Set.of(providers));
  }

  private void assertNothingSaved() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
    assertThat(helpRequests.recordedEvents()).isEmpty();
  }

  @DisplayName(
      "Lena (role cook) asks the Community and Grandma Avatar about her burnt roast chicken, recipe Roast chicken, photos [burnt-chicken-1, burnt-chicken-2], Sat 18:40 -> the id from the id generator is returned; the saved request has type STEPS_TO_MITIGATE_CATASTROPHE, the recipe, the photos in order, Community until 18:55 and Grandma until 18:45 -- B4-S01 US-CA-04 cmd Request help Catastrophe R1.E2 INV-19 INV-20 INV-26")
  @Test
  void cookRequestsHelpWithACatastropheAndGetsTheNewId() {
    HelpRequestId id =
        useCase.execute(
            lenaAsksAboutBurntChicken(
                LENA_AS_COOK, HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR));

    assertThat(id).isEqualTo(HelpRequests.HELP_REQUEST_ID);
    assertThat(helpRequests.findById(id))
        .hasValueSatisfying(
            saved -> {
              assertThat(saved.requester()).isEqualTo(HelpRequests.LENA);
              assertThat(saved.type()).isEqualTo(HelpType.STEPS_TO_MITIGATE_CATASTROPHE);
              assertThat(saved.recipe()).isEqualTo(HelpRequests.ROAST_CHICKEN);
              assertThat(saved.pictures()).isEqualTo(HelpRequests.BURNT_CHICKEN_PHOTOS);
              assertThat(saved.askedProviders())
                  .containsExactlyInAnyOrder(
                      new AskedProvider(
                          HelpProviderType.COMMUNITY,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(15))),
                      new AskedProvider(
                          HelpProviderType.GRANDMA_AVATAR,
                          HelpRequests.SATURDAY_18_40.instant().plus(Duration.ofMinutes(5))));
            });
  }

  @DisplayName(
      "the HelpRequested event with the recipe and the photos is saved with the request -- B4-S01 US-CA-04 evt HelpRequested")
  @Test
  void helpRequestedIsSavedWithTheRequest() {
    useCase.execute(lenaAsksAboutBurntChicken(LENA_AS_COOK, HelpProviderType.GRANDMA_AVATAR));

    assertThat(helpRequests.recordedEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            event -> {
              assertThat(event.recipe()).isEqualTo(HelpRequests.ROAST_CHICKEN);
              assertThat(event.pictures()).isEqualTo(HelpRequests.BURNT_CHICKEN_PHOTOS);
            });
  }

  @DisplayName(
      "Tom, a cook on the free plan, asks Grandma about his collapsed souffle without a recipe and without photos -> the request is saved with no recipe and no pictures -- B4-S01 US-CA-04 R1.E3 R2.E3 INV-18 INV-19")
  @Test
  void aFreePlanCookAsksWithoutRecipeOrPhotos() {
    Actor tomOnTheFreePlan =
        new Actor(
            new CookId(UUID.fromString("0e1d2c3b-4a59-4678-8a9b-0c1d2e3f4a5b")),
            Set.of(Role.COOK),
            false);

    HelpRequestId id =
        useCase.execute(
            new RequestHelpWithCatastrophe.Command(
                tomOnTheFreePlan,
                "My souffle collapsed",
                "It fell flat when I opened the oven",
                Optional.empty(),
                List.of(),
                Set.of(HelpProviderType.GRANDMA_AVATAR)));

    assertThat(helpRequests.findById(id))
        .hasValueSatisfying(
            saved -> {
              assertThat(saved.requester()).isEqualTo(tomOnTheFreePlan.id());
              assertThat(saved.recipe()).isNull();
              assertThat(saved.pictures()).isEmpty();
            });
  }

  @DisplayName(
      "Lena asks chef Marco -> ChefNotAllowed passes through unchanged (rule INV-01), nothing saved -- B4-S01 US-CA-04 R1.E4 INV-18")
  @Test
  void askingAChefIsRefused() {
    assertThatThrownBy(
            () -> useCase.execute(lenaAsksAboutBurntChicken(LENA_AS_COOK, HelpProviderType.CHEF)))
        .isInstanceOfSatisfying(
            ChefNotAllowed.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-01"));
    assertNothingSaved();
  }

  @DisplayName(
      "no provider chosen -> NoHelpProviderChosen passes through unchanged, nothing saved -- B4-S01 US-CA-04 INV-18")
  @Test
  void noProviderChosenIsRefused() {
    assertThatThrownBy(() -> useCase.execute(lenaAsksAboutBurntChicken(LENA_AS_COOK)))
        .isInstanceOf(NoHelpProviderChosen.class);
    assertNothingSaved();
  }

  @DisplayName(
      "11 photos -> TooManyPictures passes through unchanged (rule INV-38), nothing saved -- B4-S01 US-CA-04 INV-20 INV-38")
  @Test
  void elevenPhotosAreRefused() {
    List<URI> elevenPhotos =
        IntStream.rangeClosed(1, 11)
            .mapToObj(i -> URI.create("https://img.larder.example/burnt-chicken-" + i + ".jpg"))
            .toList();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        Optional.of(HelpRequests.ROAST_CHICKEN),
                        elevenPhotos,
                        HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOfSatisfying(
            TooManyPictures.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-38"));
    assertNothingSaved();
  }

  @DisplayName(
      "a photo as an http link -> PictureLinkNotSecure passes through unchanged (rule INV-38), nothing saved -- B4-S01 US-CA-04 INV-38 SEC-07")
  @Test
  void aPhotoThatIsNotAnHttpsLinkIsRefused() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        Optional.of(HelpRequests.ROAST_CHICKEN),
                        List.of(URI.create("http://img.larder.example/burnt-chicken-1.jpg")),
                        HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOfSatisfying(
            PictureLinkNotSecure.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-38"));
    assertNothingSaved();
  }

  @DisplayName(
      "an actor without role cook (only chef) -> NotAuthorized, nothing saved -- B4-S01 US-CA-04 ADR0013")
  @Test
  void actorWithoutCookRoleIsRefused() {
    Actor chefOnly = new Actor(HelpRequests.LENA, Set.of(Role.CHEF), false);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsksAboutBurntChicken(chefOnly, HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOf(NotAuthorized.class);
    assertNothingSaved();
  }
}
