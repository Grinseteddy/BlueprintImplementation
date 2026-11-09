package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.ChefNotAllowed;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequested;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.IngredientRequired;
import org.larder.cookingassistance.domain.helprequest.NoHelpProviderChosen;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.domain.helprequest.RecipeRequired;
import org.larder.cookingassistance.domain.helprequest.SubstituteRequirementsRequired;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Request help ingredients (B2-S01, US-CA-02) with contract-checked fakes, a fixed clock
 * and a predictable id generator. Access per ADR0013: role cook.
 */
class RequestHelpWithIngredientTest {

  static final Actor LENA_AS_COOK = new Actor(HelpRequests.LENA, Set.of(Role.COOK), true);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final RequestHelpWithIngredient useCase =
      new RequestHelpWithIngredient(
          helpRequests, () -> HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.MONDAY_18_00);

  static RequestHelpWithIngredient.Command lenaAsksGrandma(Actor actor) {
    return lenaAsks(
        actor,
        HelpRequests.TOMATO_SOUP,
        HelpRequests.CREME_FRAICHE,
        "200 g, not available",
        HelpProviderType.GRANDMA_AVATAR);
  }

  static RequestHelpWithIngredient.Command lenaAsks(
      Actor actor,
      RecipeId recipe,
      IngredientId ingredient,
      String requirements,
      HelpProviderType... providers) {
    return new RequestHelpWithIngredient.Command(
        actor,
        "Creme fraiche in tomato soup",
        "The shop has none",
        recipe,
        ingredient,
        requirements,
        Set.of(providers));
  }

  private void assertNothingSaved() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
    assertThat(helpRequests.recordedEvents()).isEmpty();
  }

  @DisplayName(
      "Lena (role cook) asks Grandma Avatar for a substitute for creme fraiche in tomato soup -> the id from the id generator is returned; a request with Lena, the ingredient and the requirements is saved -- B2-S01 US-CA-02 cmd Request help ingredients")
  @Test
  void cookRequestsHelpWithAnIngredientAndGetsTheNewId() {
    HelpRequestId id = useCase.execute(lenaAsksGrandma(LENA_AS_COOK));

    assertThat(id).isEqualTo(HelpRequests.HELP_REQUEST_ID);
    assertThat(helpRequests.findById(id))
        .hasValueSatisfying(
            saved -> {
              assertThat(saved.requester()).isEqualTo(HelpRequests.LENA);
              assertThat(saved.ingredient()).isEqualTo(HelpRequests.CREME_FRAICHE);
              assertThat(saved.substituteRequirements()).isEqualTo("200 g, not available");
            });
  }

  @DisplayName(
      "the HelpRequested event with the ingredient is saved with the request -- B2-S01 US-CA-02 evt HelpRequested")
  @Test
  void helpRequestedIsSavedWithTheRequest() {
    useCase.execute(lenaAsksGrandma(LENA_AS_COOK));

    assertThat(helpRequests.recordedEvents())
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequested.class,
            event -> assertThat(event.ingredient()).isEqualTo(HelpRequests.CREME_FRAICHE));
  }

  @DisplayName(
      "Tom, a cook on the free plan, asks the Community -> the request is saved -- B2-S01 US-CA-02 INV-10 R1.E3")
  @Test
  void aCookOnTheFreePlanMayAsk() {
    Actor tomOnTheFreePlan =
        new Actor(
            new CookId(UUID.fromString("0e1d2c3b-4a59-4678-8a9b-0c1d2e3f4a5b")),
            Set.of(Role.COOK),
            false);

    HelpRequestId id =
        useCase.execute(
            lenaAsks(
                tomOnTheFreePlan,
                HelpRequests.TOMATO_SOUP,
                HelpRequests.CREME_FRAICHE,
                "200 g, not available",
                HelpProviderType.COMMUNITY));

    assertThat(helpRequests.findById(id)).isPresent();
  }

  @DisplayName(
      "asking a Chef -> ChefNotAllowed passes through unchanged (rule INV-01), nothing saved -- B2-S01 US-CA-02 INV-10")
  @Test
  void chefNotAllowedPassesThroughUnchanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.TOMATO_SOUP,
                        HelpRequests.CREME_FRAICHE,
                        "200 g, not available",
                        HelpProviderType.CHEF)))
        .isInstanceOfSatisfying(
            ChefNotAllowed.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-01"));
    assertNothingSaved();
  }

  @DisplayName(
      "no provider chosen -> NoHelpProviderChosen passes through unchanged, nothing saved -- B2-S01 US-CA-02 INV-10")
  @Test
  void noHelpProviderChosenPassesThroughUnchanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.TOMATO_SOUP,
                        HelpRequests.CREME_FRAICHE,
                        "200 g, not available")))
        .isInstanceOf(NoHelpProviderChosen.class);
    assertNothingSaved();
  }

  @DisplayName(
      "no recipe -> RecipeRequired passes through unchanged, nothing saved -- B2-S01 US-CA-02 INV-11")
  @Test
  void recipeRequiredPassesThroughUnchanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        null,
                        HelpRequests.CREME_FRAICHE,
                        "200 g, not available",
                        HelpProviderType.COMMUNITY)))
        .isInstanceOf(RecipeRequired.class);
    assertNothingSaved();
  }

  @DisplayName(
      "no ingredient -> IngredientRequired passes through unchanged (rule INV-11), nothing saved -- B2-S01 US-CA-02 INV-11")
  @Test
  void ingredientRequiredPassesThroughUnchanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.TOMATO_SOUP,
                        null,
                        "200 g, not available",
                        HelpProviderType.COMMUNITY)))
        .isInstanceOfSatisfying(
            IngredientRequired.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-11"));
    assertNothingSaved();
  }

  @DisplayName(
      "no requirements -> SubstituteRequirementsRequired passes through unchanged (rule INV-11), nothing saved -- B2-S01 US-CA-02 INV-11 S2")
  @Test
  void substituteRequirementsRequiredPassesThroughUnchanged() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    lenaAsks(
                        LENA_AS_COOK,
                        HelpRequests.TOMATO_SOUP,
                        HelpRequests.CREME_FRAICHE,
                        null,
                        HelpProviderType.COMMUNITY)))
        .isInstanceOfSatisfying(
            SubstituteRequirementsRequired.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-11"));
    assertNothingSaved();
  }

  @DisplayName(
      "an actor without role cook (only chef) -> NotAuthorized, nothing saved -- B2-S01 US-CA-02 ADR0013")
  @Test
  void actorWithoutCookRoleIsRefused() {
    Actor chefOnly = new Actor(HelpRequests.LENA, Set.of(Role.CHEF), false);

    assertThatThrownBy(() -> useCase.execute(lenaAsksGrandma(chefOnly)))
        .isInstanceOf(NotAuthorized.class);
    assertNothingSaved();
  }
}
