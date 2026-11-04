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
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpAlreadyRejected;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRejected;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.domain.helprequest.RejectionNoteRequired;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Reject help (B3-S05, US-CA-03) with contract-checked fakes, fixed clock. Access per
 * ADR0013: only the requester. One retry on ConcurrencyConflict (ADR0010).
 */
class RejectHelpTest {

  static final Actor LENA = new Actor(HelpRequests.LENA, Set.of(Role.COOK), false);
  static final Actor TOM = CancelHelpRequestTest.TOM;
  static final Clock FRIDAY_20_00 =
      Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final RejectHelp useCase = new RejectHelp(helpRequests, FRIDAY_20_00);

  @BeforeEach
  void grandmaAnsweredLenasRequest() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    helpRequests.save(request);
  }

  static RejectHelp.Command rejectionBy(Actor actor, String note) {
    return new RejectHelp.Command(actor, HelpRequests.HELP_REQUEST_ID, HelpRequests.HELP_ID, note);
  }

  @DisplayName(
      "Lena (the requester) rejects Grandma's Help at Fri 20:00 with note 'warm water didn't help' -> the saved request is OPEN, the Help rejected, Grandma asked again until Sat 08:00 -- B3-S05 US-CA-03 cmd Reject help INV-06")
  @Test
  void lenaRejectsGrandmasHelpWithANote() {
    useCase.execute(rejectionBy(LENA, "warm water didn't help"));

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(stored.helps()).singleElement().extracting(Help::isRejected).isEqualTo(true);
    assertThat(stored.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR,
                Instant.parse("2026-11-07T07:00:00Z"),
                ProviderState.ASKED));
  }

  @DisplayName(
      "the HelpRejected event is saved with the request -- B3-S05 US-CA-03 evt HelpRejected")
  @Test
  void theRejectedEventIsSavedWithTheRequest() {
    useCase.execute(rejectionBy(LENA, "warm water didn't help"));

    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRejected.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRejected.class,
            event -> assertThat(event.note()).isEqualTo("warm water didn't help"));
  }

  @DisplayName(
      "unknown help request id -> HelpRequestNotFound, nothing saved -- B3-S05 US-CA-03 404")
  @Test
  void unknownRequestIsNotFound() {
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new RejectHelp.Command(
                        LENA, unknown, HelpRequests.HELP_ID, "warm water didn't help")))
        .isInstanceOf(HelpRequestNotFound.class);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRejected.class::isInstance);
  }

  @DisplayName(
      "Tom (not the requester) rejects the Help on Lena's request -> NotAuthorized, nothing saved -- B3-S05 US-CA-03 ADR0013 requester only")
  @Test
  void onlyTheRequesterRejects() {
    assertThatThrownBy(() -> useCase.execute(rejectionBy(TOM, "warm water didn't help")))
        .isInstanceOf(NotAuthorized.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRejected.class::isInstance);
  }

  @DisplayName(
      "Lena rejects with a blank note -> RejectionNoteRequired passes through unchanged (rule INV-06), nothing saved -- B3-S05 US-CA-03 INV-06")
  @Test
  void aMissingNotePassesTheRejectionThrough() {
    assertThatThrownBy(() -> useCase.execute(rejectionBy(LENA, " ")))
        .isInstanceOfSatisfying(
            RejectionNoteRequired.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-06"));
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRejected.class::isInstance);
  }

  @DisplayName(
      "Lena rejects the same Help twice (Grandma answered again in between) -> HelpAlreadyRejected passes through unchanged (rule INV-39), nothing saved -- B3-S05 US-CA-03 INV-39")
  @Test
  void aRejectedHelpPassesTheRejectionThrough() {
    useCase.execute(rejectionBy(LENA, "warm water didn't help"));
    HelpRequest request = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_20_00);
    helpRequests.save(request);
    int eventsBefore = helpRequests.recordedEvents().size();

    assertThatThrownBy(() -> useCase.execute(rejectionBy(LENA, "still splits")))
        .isInstanceOfSatisfying(
            HelpAlreadyRejected.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-39"));
    assertThat(helpRequests.recordedEvents()).hasSize(eventsBefore);
  }

  @DisplayName(
      "another writer saves first (Grandma answers once more in between) -> reload and retry once: Grandma's first Help rejected, her second kept, Grandma asked again until Sat 08:00 -- B3-S05 US-CA-03 ADR0010")
  @Test
  void anotherWriterFirstRetriesOnce() {
    helpRequests.interleave(this::grandmaAnswersAgainMeanwhile);

    useCase.execute(rejectionBy(LENA, "warm water didn't help"));

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.helps())
        .extracting(Help::id, Help::isRejected)
        .containsExactly(
            tuple(HelpRequests.HELP_ID, true), tuple(HelpRequests.OTHER_HELP_ID, false));
    assertThat(stored.askedProviders())
        .extracting(AskedProvider::deadline)
        .containsExactly(Instant.parse("2026-11-07T07:00:00Z"));
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
