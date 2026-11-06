package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpAccepted;
import org.larder.cookingassistance.domain.helprequest.HelpAlreadyRejected;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpNotInRequest;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Accept help (B3-S04, US-CA-03) with contract-checked fakes, fixed clock. Access per
 * ADR0013: only the requester. One retry on ConcurrencyConflict (ADR0010).
 */
class AcceptHelpTest {

  static final Actor LENA = new Actor(HelpRequests.LENA, Set.of(Role.COOK), false);
  static final Actor TOM = CancelHelpRequestTest.TOM;

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final AcceptHelp useCase = new AcceptHelp(helpRequests, HelpRequests.FRIDAY_18_10);

  @BeforeEach
  void grandmaAnsweredLenasRequest() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    helpRequests.save(request);
  }

  static AcceptHelp.Command acceptanceBy(Actor actor, HelpId helpId) {
    return new AcceptHelp.Command(actor, HelpRequests.HELP_REQUEST_ID, helpId);
  }

  @DisplayName(
      "Lena (the requester) accepts Grandma's Help -> the saved request is CLOSED with the Help accepted -- B3-S04 US-CA-03 cmd Accept help")
  @Test
  void lenaAcceptsGrandmasHelp() {
    useCase.execute(acceptanceBy(LENA, HelpRequests.HELP_ID));

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(stored.helps()).singleElement().extracting(Help::isAccepted).isEqualTo(true);
  }

  @DisplayName(
      "the HelpAccepted event is saved with the request -- B3-S04 US-CA-03 evt HelpAccepted")
  @Test
  void theAcceptedEventIsSavedWithTheRequest() {
    useCase.execute(acceptanceBy(LENA, HelpRequests.HELP_ID));

    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpAccepted.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpAccepted.class,
            event -> assertThat(event.helpId()).isEqualTo(HelpRequests.HELP_ID));
  }

  @DisplayName(
      "unknown help request id -> HelpRequestNotFound, nothing saved -- B3-S04 US-CA-03 404")
  @Test
  void unknownRequestIsNotFound() {
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () -> useCase.execute(new AcceptHelp.Command(LENA, unknown, HelpRequests.HELP_ID)))
        .isInstanceOf(HelpRequestNotFound.class);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpAccepted.class::isInstance);
  }

  @DisplayName(
      "Tom (not the requester) accepts the Help on Lena's request -> NotAuthorized, nothing saved -- B3-S04 US-CA-03 ADR0013 requester only")
  @Test
  void onlyTheRequesterAccepts() {
    assertThatThrownBy(() -> useCase.execute(acceptanceBy(TOM, HelpRequests.HELP_ID)))
        .isInstanceOf(NotAuthorized.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpAccepted.class::isInstance);
  }

  @DisplayName(
      "Lena accepts a Help id that belongs to no Help of her request -> HelpNotInRequest passes through unchanged (rule INV-05), nothing saved -- B3-S04 US-CA-03 INV-05")
  @Test
  void aForeignHelpPassesTheRejectionThrough() {
    assertThatThrownBy(() -> useCase.execute(acceptanceBy(LENA, HelpRequests.OTHER_HELP_ID)))
        .isInstanceOfSatisfying(
            HelpNotInRequest.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-05"));
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpAccepted.class::isInstance);
  }

  @DisplayName(
      "Lena accepts on an OPEN request (no Help yet) -> InvalidHelpRequestState passes through unchanged, nothing saved -- B3-S04 US-CA-03 lifecycle")
  @Test
  void anOpenRequestPassesTheRejectionThrough() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    assertThatThrownBy(() -> useCase.execute(acceptanceBy(LENA, HelpRequests.HELP_ID)))
        .isInstanceOfSatisfying(
            InvalidHelpRequestState.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("LIFECYCLE"));
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpAccepted.class::isInstance);
  }

  @DisplayName(
      "Lena accepts the Help she rejected before -> HelpAlreadyRejected passes through unchanged (rule INV-39), nothing saved -- B3-S04 US-CA-03 INV-39")
  @Test
  void aRejectedHelpPassesTheRejectionThrough() {
    HelpRequest request = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    request.reject(HelpRequests.HELP_ID, "warm water didn't help", at("2026-11-06T19:00:00Z"));
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        at("2026-11-06T20:00:00Z"));
    helpRequests.save(request);

    assertThatThrownBy(() -> useCase.execute(acceptanceBy(LENA, HelpRequests.HELP_ID)))
        .isInstanceOfSatisfying(
            HelpAlreadyRejected.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-39"));
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpAccepted.class::isInstance);
  }

  private static Clock at(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  @DisplayName(
      "another writer saves first (Grandma answers once more in between) -> reload and retry once: the request is CLOSED with Grandma's first Help accepted and her second kept -- B3-S04 US-CA-03 ADR0010")
  @Test
  void anotherWriterFirstRetriesOnce() {
    helpRequests.interleave(this::grandmaAnswersAgainMeanwhile);

    useCase.execute(acceptanceBy(LENA, HelpRequests.HELP_ID));

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(stored.helps())
        .extracting(Help::id, Help::isAccepted)
        .containsExactly(
            tuple(HelpRequests.HELP_ID, true), tuple(HelpRequests.OTHER_HELP_ID, false));
  }

  /** Another writer: Grandma answers the same request once more and saves first. */
  private void grandmaAnswersAgainMeanwhile() {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    meanwhile.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);
    helpRequests.save(meanwhile);
  }
}
