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

  @Label(
      "for any request time and any rejection time after the answer, every asked provider's new deadline is exactly rejection time + 12 h, whatever the earlier deadline was -- B3-S05 US-CA-03 INV-06 property")
  @Property(tries = 1000, seed = SEED)
  void newDeadlineIsAlwaysRejectionTimePlusTwelveHours(
      @ForAll @LongRange(min = 1_577_836_800_000L, max = 2_208_988_800_000L) long epochMillis,
      @ForAll @LongRange(min = 0, max = 43_200_000L) long answeredAfterMillis,
      @ForAll @LongRange(min = 0, max = 172_800_000L) long rejectedAfterAnswerMillis) {
    Instant requestedAt = Instant.ofEpochMilli(epochMillis);
    Instant answeredAt = requestedAt.plusMillis(answeredAfterMillis);
    Instant rejectedAt = answeredAt.plusMillis(rejectedAfterAnswerMillis);
    HelpRequest request =
        lenaAsksAt(requestedAt, HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
    grandmaAnswersAt(request, answeredAt);

    request.reject(HelpRequestTest.HELP_ID, "Still splits", fixedAt(rejectedAt));

    assertThat(request.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsOnly(rejectedAt.plus(Duration.ofHours(12)));
  }

  @Label(
      "for any request time r and any instant t: an answer at t counts iff t <= r + 12 h, and timing out at t succeeds iff t > r + 12 h - never both, never neither -- B3-S02,B3-S06 US-CA-03 INV-09 property")
  @Property(tries = 1000, seed = SEED)
  void answerCountsExactlyUntilTheDeadline(
      @ForAll @LongRange(min = 1_577_836_800_000L, max = 2_208_988_800_000L) long epochMillis,
      @ForAll @LongRange(min = -43_200_000L, max = 43_200_000L) long fromDeadlineMillis) {
    Instant requestedAt = Instant.ofEpochMilli(epochMillis);
    Instant t = requestedAt.plus(Duration.ofHours(12)).plusMillis(fromDeadlineMillis);
    HelpRequest toAnswer = lenaAsksAt(requestedAt, HelpProviderType.GRANDMA_AVATAR);
    HelpRequest toTimeOut = lenaAsksAt(requestedAt, HelpProviderType.GRANDMA_AVATAR);

    if (fromDeadlineMillis <= 0) {
      grandmaAnswersAt(toAnswer, t);
      assertThat(toAnswer.helps()).hasSize(1);
      assertThatThrownBy(() -> toTimeOut.timeOut(fixedAt(t))).isInstanceOf(DeadlineNotPassed.class);
      assertThat(toTimeOut.status()).isEqualTo(HelpRequestStatus.OPEN);
    } else {
      assertThatThrownBy(() -> grandmaAnswersAt(toAnswer, t)).isInstanceOf(AnswerTooLate.class);
      assertThat(toAnswer.helps()).isEmpty();
      toTimeOut.timeOut(fixedAt(t));
      assertThat(toTimeOut.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    }
  }

  private static HelpRequest lenaAsksAt(Instant requestedAt, HelpProviderType... providers) {
    return HelpRequest.requestHelpWithPreparationStep(
        HelpRequestTest.HELP_REQUEST_ID,
        HelpRequestTest.LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 keeps splitting",
        HelpRequestTest.HOLLANDAISE,
        HelpRequestTest.STEP_4,
        Set.of(providers),
        fixedAt(requestedAt));
  }

  private static void grandmaAnswersAt(HelpRequest request, Instant answeredAt) {
    request.provideHelp(
        HelpRequestTest.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        HelpRequestTest.BUTTER_IN_A_THIN_STREAM,
        fixedAt(answeredAt));
  }

  private static Clock fixedAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }
}
