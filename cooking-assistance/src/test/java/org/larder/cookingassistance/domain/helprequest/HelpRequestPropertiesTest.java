package org.larder.cookingassistance.domain.helprequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;

/**
 * Property tests of the Help Request aggregate for the quantitative and cardinality rules of
 * increment 0 (INV-01, INV-09 deadline, INV-38). Run by jqwik; tries and seed per ADR0017.
 */
class HelpRequestPropertiesTest {

  /** Fixed seed (ADR0017); logged in the step 6 journal. */
  static final String SEED = "20261031";

  @Label(
      "for every non-empty choice of providers from {Community, Grandma Avatar} the request is created and exactly those providers are asked; every choice containing a Chef is rejected with ChefNotAllowed -- B3-S01 US-CA-03 INV-01 property")
  @Property(tries = 1000, seed = SEED)
  void providersAreExactlyTheChosenNonChefProviders(
      @ForAll @Size(min = 1) Set<HelpProviderType> providers) {
    HelpProviderType[] chosen = providers.toArray(HelpProviderType[]::new);
    if (providers.contains(HelpProviderType.CHEF)) {
      assertThatThrownBy(() -> HelpRequestTest.lenaAsksAboutStep4(chosen))
          .isInstanceOf(ChefNotAllowed.class);
    } else {
      assertThat(HelpRequestTest.lenaAsksAboutStep4(chosen).askedProviderTypes())
          .containsExactlyInAnyOrderElementsOf(providers);
    }
  }

  @Label(
      "for any request time t, every asked provider's deadline is t + 12 h -- B3-S01 US-CA-03 INV-09 property (deadline only)")
  @Property(tries = 1000, seed = SEED)
  void deadlineIsAlwaysTwelveHoursAfterTheRequest(
      @ForAll @LongRange(min = 1_577_836_800_000L, max = 2_208_988_800_000L) long epochMillis) {
    Instant requestedAt = Instant.ofEpochMilli(epochMillis);

    HelpRequest request =
        HelpRequest.requestHelpWithPreparationStep(
            HelpRequestTest.HELP_REQUEST_ID,
            HelpRequestTest.LENA,
            "How do I keep the sauce from splitting?",
            "Step 4 keeps splitting",
            HelpRequestTest.HOLLANDAISE,
            HelpRequestTest.STEP_4,
            Set.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR),
            Clock.fixed(requestedAt, ZoneOffset.UTC));

    assertThat(request.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsOnly(requestedAt.plus(Duration.ofHours(12)));
  }

  @Label(
      "for any number n of pictures from 0 to 30, the answer is accepted exactly when n <= 10, otherwise TooManyPictures -- B3-S02 US-CA-03 INV-38 property")
  @Property(tries = 1000, seed = SEED)
  void answersAreAcceptedExactlyUpToTenPictures(@ForAll @IntRange(min = 0, max = 30) int n) {
    HelpRequest request = HelpRequestTest.lenaAsksGrandmaAboutStep4();
    PreparationStepExplanation answer = HelpRequestTest.explanationWith(HelpRequestTest.photos(n));

    if (n <= 10) {
      request.provideHelp(
          HelpRequestTest.HELP_ID,
          HelpProvider.grandmaAvatar(),
          "Butter",
          answer,
          HelpRequestTest.FRIDAY_18_10);
      assertThat(request.helps()).hasSize(1);
    } else {
      assertThatThrownBy(
              () ->
                  request.provideHelp(
                      HelpRequestTest.HELP_ID,
                      HelpProvider.grandmaAvatar(),
                      "Butter",
                      answer,
                      HelpRequestTest.FRIDAY_18_10))
          .isInstanceOf(TooManyPictures.class);
      assertThat(request.helps()).isEmpty();
    }
  }
}
