package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.ChefNotAllowed;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequested;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.NoHelpProviderChosen;
import org.larder.cookingassistance.domain.helprequest.PreparationStepRequired;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.domain.helprequest.RecipeRequired;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Request help Preparation step (B3-S01, US-CA-03) with contract-checked fakes, a fixed
 * clock and a predictable id generator. Access per ADR0013: role cook.
 */
class RequestHelpWithPreparationStepTest {

  static final Actor LENA_AS_COOK = new Actor(HelpRequests.LENA, Set.of(Role.COOK), false);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final RequestHelpWithPreparationStep useCase =
      new RequestHelpWithPreparationStep(
          helpRequests, () -> HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.FRIDAY_18_00);

  static RequestHelpWithPreparationStep.Command lenaAsksGrandmaAboutStep4(Actor actor) {
    return lenaAsks(
        actor, HelpRequests.HOLLANDAISE, HelpRequests.STEP_4, HelpProviderType.GRANDMA_AVATAR);
  }

  static RequestHelpWithPreparationStep.Command lenaAsks(
      Actor actor, RecipeId recipe, HowToStepId step, HelpProviderType... providers) {
    return new RequestHelpWithPreparationStep.Command(
        actor,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        recipe,
        step,
        Set.of(providers));
  }

  private void assertNothingSaved() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
    assertThat(helpRequests.recordedEvents()).isEmpty();
  }

  @DisplayName(
      "Lena (role cook) requests help with step 4 from Grandma Avatar -> the id from the id generator is returned and a request with Lena as requester is saved -- B3-S01 US-CA-03 cmd Request help Preparation step")
  @Test
  void cookRequestsHelpAndGetsTheNewId() {
    HelpRequestId id = useCase.execute(lenaAsksGrandmaAboutStep4(LENA_AS_COOK));

    assertThat(id).isEqualTo(HelpRequests.HELP_REQUEST_ID);
    assertThat(helpRequests.findById(id))
        .hasValueSatisfying(saved -> assertThat(saved.requester()).isEqualTo(HelpRequests.LENA));
  }

  @DisplayName(
      "the HelpRequested event is saved with the request -- B3-S01 US-CA-03 evt HelpRequested")
  @Test
  void helpRequestedIsSavedWithTheRequest() {
    useCase.execute(lenaAsksGrandmaAboutStep4(LENA_AS_COOK));

    assertThat(helpRequests.recordedEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            event -> assertThat(event.helpRequestId()).isEqualTo(HelpRequests.HELP_REQUEST_ID));
  }

  @DisplayName(
      "asking a Chef -> ChefNotAllowed passes through unchanged (rule INV-01), nothing saved -- B3-S01 US-CA-03 INV-01")
  @Test
  void chefNotAllowedPassesThrough() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.HOLLANDAISE,
                        HelpRequests.STEP_4,
                        HelpProviderType.CHEF)))
        .isInstanceOfSatisfying(
            ChefNotAllowed.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-01"));
    assertNothingSaved();
  }

  @DisplayName(
      "asking nobody -> NoHelpProviderChosen passes through unchanged (rule INV-01), nothing saved -- B3-S01 US-CA-03 INV-01")
  @Test
  void noHelpProviderChosenPassesThrough() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(LENA_AS_COOK, HelpRequests.HOLLANDAISE, HelpRequests.STEP_4)))
        .isInstanceOfSatisfying(
            NoHelpProviderChosen.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-01"));
    assertNothingSaved();
  }

  @DisplayName(
      "no recipe -> RecipeRequired passes through unchanged (rule INV-02), nothing saved -- B3-S01 US-CA-03 INV-02")
  @Test
  void recipeRequiredPassesThrough() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK, null, HelpRequests.STEP_4, HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOfSatisfying(
            RecipeRequired.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-02"));
    assertNothingSaved();
  }

  @DisplayName(
      "no step -> PreparationStepRequired passes through unchanged (rule INV-02), nothing saved -- B3-S01 US-CA-03 INV-02")
  @Test
  void preparationStepRequiredPassesThrough() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.HOLLANDAISE,
                        null,
                        HelpProviderType.GRANDMA_AVATAR)))
        .isInstanceOfSatisfying(
            PreparationStepRequired.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-02"));
    assertNothingSaved();
  }

  @DisplayName(
      "an actor without role cook (e.g. only chef) -> NotAuthorized, nothing saved -- B3-S01 US-CA-03 ADR0013")
  @Test
  void actorWithoutCookRoleIsRefused() {
    Actor chefOnly = new Actor(HelpRequests.LENA, Set.of(Role.CHEF), false);

    assertThatThrownBy(() -> useCase.execute(lenaAsksGrandmaAboutStep4(chefOnly)))
        .isInstanceOf(NotAuthorized.class);
    assertNothingSaved();
  }
}
