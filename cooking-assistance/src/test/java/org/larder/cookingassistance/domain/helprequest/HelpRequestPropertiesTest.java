package org.larder.cookingassistance.domain.helprequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.Size;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Property tests of the Help Request aggregate for the quantitative and cardinality rules of
 * increments 0 to 2 (INV-01, INV-06 rejection note, INV-09 deadline, answer and time-out timing,
 * INV-10, INV-12, INV-14, INV-17, INV-38). Run by jqwik; tries and seed per ADR0017.
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

  @Label(
      "for every non-empty choice of providers an ingredient request asks exactly those providers; every choice containing a Chef is rejected with ChefNotAllowed -- B2-S01 US-CA-02 INV-10 property")
  @Property(tries = 1000, seed = SEED)
  void ingredientRequestAsksExactlyTheChosenNonChefProviders(
      @ForAll @Size(min = 1) Set<HelpProviderType> providers) {
    HelpProviderType[] chosen = providers.toArray(HelpProviderType[]::new);
    Instant requestedAt = HelpRequestIngredientTest.MONDAY_18_00.instant();
    if (providers.contains(HelpProviderType.CHEF)) {
      assertThatThrownBy(() -> lenaAsksForASubstituteAt(requestedAt, chosen))
          .isInstanceOf(ChefNotAllowed.class);
    } else {
      assertThat(lenaAsksForASubstituteAt(requestedAt, chosen).askedProviderTypes())
          .containsExactlyInAnyOrderElementsOf(providers);
    }
  }

  @Label(
      "for any 1..5 substitutes of 1..5 ingredients the answer counts when every name says something, and is rejected with NoSubstituteProposed when any name is blank -- B2-S02 US-CA-02 INV-12 property (step 3 r13)")
  @Property(tries = 1000, seed = SEED)
  void anAnswerCountsExactlyWhenEveryIngredientHasAName(
      @ForAll @Size(min = 1, max = 5)
          List<@Size(min = 1, max = 5) List<@From("nameOrBlank") String>> proposed) {
    Instant requestedAt = HelpRequestIngredientTest.MONDAY_18_00.instant();
    HelpRequest request = lenaAsksForASubstituteAt(requestedAt, HelpProviderType.GRANDMA_AVATAR);
    List<Substitute> substitutes =
        proposed.stream().map(HelpRequestPropertiesTest::substituteOf).toList();

    if (proposed.stream().flatMap(List::stream).anyMatch(String::isBlank)) {
      assertThatThrownBy(() -> grandmaProposesAt(request, requestedAt, substitutes))
          .isInstanceOf(NoSubstituteProposed.class);
      assertThat(request.helps()).isEmpty();
    } else {
      grandmaProposesAt(request, requestedAt, substitutes);
      assertThat(request.helps()).hasSize(1);
    }
  }

  @Provide
  Arbitrary<String> nameOrBlank() {
    return Arbitraries.frequencyOf(
        Tuple.of(9, Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)),
        Tuple.of(1, Arbitraries.of("", " ", "\t ")));
  }

  @Label(
      "for any request time and any rejection time after the answer, every asked provider's new deadline is exactly rejection time + 24 h -- B3-S05 US-CA-02 INV-14 property")
  @Property(tries = 1000, seed = SEED)
  void newDeadlineOfAnIngredientRequestIsAlwaysRejectionTimePlus24Hours(
      @ForAll @LongRange(min = 1_577_836_800_000L, max = 2_208_988_800_000L) long epochMillis,
      @ForAll @LongRange(min = 0, max = 43_200_000L) long answeredAfterMillis,
      @ForAll @LongRange(min = 0, max = 172_800_000L) long rejectedAfterAnswerMillis) {
    Instant requestedAt = Instant.ofEpochMilli(epochMillis);
    Instant answeredAt = requestedAt.plusMillis(answeredAfterMillis);
    Instant rejectedAt = answeredAt.plusMillis(rejectedAfterAnswerMillis);
    HelpRequest request =
        lenaAsksForASubstituteAt(
            requestedAt, HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
    grandmaProposesAt(request, answeredAt, List.of(HelpRequestIngredientTest.YOGURT));

    request.reject(HelpRequests.HELP_ID, "Too sour", fixedAt(rejectedAt));

    assertThat(request.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsOnly(rejectedAt.plus(Duration.ofHours(24)));
  }

  @Label(
      "for any request time r and any instant t: every deadline is r + 24 h, an answer at t counts iff t <= r + 24 h, and timing out at t succeeds iff t > r + 24 h -- B2-S02,B3-S06 US-CA-02 INV-17 property")
  @Property(tries = 1000, seed = SEED)
  void ingredientAnswerCountsExactlyUntilTheDeadline(
      @ForAll @LongRange(min = 1_577_836_800_000L, max = 2_208_988_800_000L) long epochMillis,
      @ForAll @LongRange(min = -86_400_000L, max = 86_400_000L) long fromDeadlineMillis) {
    Instant requestedAt = Instant.ofEpochMilli(epochMillis);
    Instant deadline = requestedAt.plus(Duration.ofHours(24));
    Instant t = deadline.plusMillis(fromDeadlineMillis);
    List<Substitute> yogurt = List.of(HelpRequestIngredientTest.YOGURT);
    HelpRequest toAnswer = lenaAsksForASubstituteAt(requestedAt, HelpProviderType.GRANDMA_AVATAR);
    HelpRequest toTimeOut = lenaAsksForASubstituteAt(requestedAt, HelpProviderType.GRANDMA_AVATAR);

    assertThat(toAnswer.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsOnly(deadline);
    if (fromDeadlineMillis <= 0) {
      grandmaProposesAt(toAnswer, t, yogurt);
      assertThat(toAnswer.helps()).hasSize(1);
      assertThatThrownBy(() -> toTimeOut.timeOut(fixedAt(t))).isInstanceOf(DeadlineNotPassed.class);
      assertThat(toTimeOut.status()).isEqualTo(HelpRequestStatus.OPEN);
    } else {
      assertThatThrownBy(() -> grandmaProposesAt(toAnswer, t, yogurt))
          .isInstanceOf(AnswerTooLate.class);
      assertThat(toAnswer.helps()).isEmpty();
      toTimeOut.timeOut(fixedAt(t));
      assertThat(toTimeOut.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    }
  }

  private static HelpRequest lenaAsksForASubstituteAt(
      Instant requestedAt, HelpProviderType... providers) {
    return HelpRequest.requestHelpWithIngredient(
        HelpRequests.HELP_REQUEST_ID,
        HelpRequests.LENA,
        "Creme fraiche in tomato soup",
        "The shop has none",
        HelpRequestIngredientTest.TOMATO_SOUP,
        HelpRequestIngredientTest.CREME_FRAICHE,
        "200 g, not available",
        Set.of(providers),
        fixedAt(requestedAt));
  }

  private static void grandmaProposesAt(
      HelpRequest request, Instant answeredAt, List<Substitute> substitutes) {
    request.provideHelp(
        HelpRequests.HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Grandma's ideas",
        new Substitutes(HelpRequestIngredientTest.TOMATO_SOUP, substitutes),
        fixedAt(answeredAt));
  }

  private static Substitute substituteOf(List<String> names) {
    return new Substitute(
        HelpRequestIngredientTest.CREME_FRAICHE,
        names.stream()
            .map(name -> new SubstituteIngredient(name, new BigDecimal("100"), Unit.GRAM))
            .toList());
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
