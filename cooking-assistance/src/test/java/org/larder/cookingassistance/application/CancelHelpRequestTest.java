package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestCancelled;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Cancel help request (B3-S03, US-CA-03) with contract-checked fakes, fixed clock. Access
 * per ADR0013: only the requester. One retry on ConcurrencyConflict (ADR0010).
 */
class CancelHelpRequestTest {

  static final Actor LENA = new Actor(HelpRequests.LENA, Set.of(Role.COOK), false);
  static final Actor TOM =
      new Actor(
          new CookId(UUID.fromString("0e1b9f4a-7c2d-4e8b-9a61-3f5d2c7b8e10")),
          Set.of(Role.COOK),
          false);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final CancelHelpRequest useCase = new CancelHelpRequest(helpRequests, HelpRequests.FRIDAY_18_10);

  @BeforeEach
  void lenasRequestToGrandmaIsStored() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  static CancelHelpRequest.Command cancellationBy(Actor actor) {
    return new CancelHelpRequest.Command(
        actor, HelpRequests.HELP_REQUEST_ID, Optional.of("an ice cube saved it"));
  }

  @DisplayName(
      "Lena (the requester) cancels her open request with note 'an ice cube saved it' -> the saved request is CANCELLED -- B3-S03 US-CA-03 cmd Cancel help request")
  @Test
  void lenaCancelsHerOpenRequest() {
    useCase.execute(cancellationBy(LENA));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
  }

  @DisplayName(
      "the HelpRequestCancelled event is saved with the request -- B3-S03 US-CA-03 evt HelpRequestCancelled")
  @Test
  void theCancelledEventIsSavedWithTheRequest() {
    useCase.execute(cancellationBy(LENA));

    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRequestCancelled.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class,
            event -> assertThat(event.note()).contains("an ice cube saved it"));
  }

  @DisplayName(
      "unknown help request id -> HelpRequestNotFound, nothing saved -- B3-S03 US-CA-03 404")
  @Test
  void unknownRequestIsNotFound() {
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () -> useCase.execute(new CancelHelpRequest.Command(LENA, unknown, Optional.empty())))
        .isInstanceOf(HelpRequestNotFound.class);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRequestCancelled.class::isInstance);
  }

  @DisplayName(
      "Tom (not the requester) cancels Lena's request -> NotAuthorized, nothing saved -- B3-S03 US-CA-03 ADR0013 requester only")
  @Test
  void onlyTheRequesterCancels() {
    assertThatThrownBy(() -> useCase.execute(cancellationBy(TOM)))
        .isInstanceOf(NotAuthorized.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRequestCancelled.class::isInstance);
  }

  @DisplayName(
      "Lena cancels a CANCELLED request -> InvalidHelpRequestState passes through unchanged (rule LIFECYCLE), nothing saved -- B3-S03 US-CA-03 INV-08 lifecycle")
  @Test
  void aCancelledRequestPassesTheRejectionThrough() {
    useCase.execute(cancellationBy(LENA));
    int eventsBefore = helpRequests.recordedEvents().size();

    assertThatThrownBy(() -> useCase.execute(cancellationBy(LENA)))
        .isInstanceOfSatisfying(
            InvalidHelpRequestState.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("LIFECYCLE"));
    assertThat(helpRequests.recordedEvents()).hasSize(eventsBefore);
  }

  @DisplayName(
      "another writer saves first (Grandma answers in between) -> the use case reloads and retries once: the request is CANCELLED and keeps Grandma's Help -- B3-S03 US-CA-03 ADR0010")
  @Test
  void anotherWriterFirstRetriesOnce() {
    helpRequests.interleave(this::grandmaAnswersMeanwhile);

    useCase.execute(cancellationBy(LENA));

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(stored.helps()).extracting(Help::id).containsExactly(HelpRequests.HELP_ID);
  }

  /** Another writer: Grandma answers the same request and saves first. */
  private void grandmaAnswersMeanwhile() {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(meanwhile);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "the retry conflicts too -> ConcurrencyConflict propagates; the request is not CANCELLED -- B3-S03 US-CA-03 ADR0010")
  @Test
  void theRetryConflictsTooAndPropagates() {
    helpRequests.interleave(
        () -> {
          grandmaAnswersMeanwhile();
          helpRequests.interleave(this::grandmaAnswersAgainMeanwhile);
        });

    assertThatThrownBy(() -> useCase.execute(cancellationBy(LENA)))
        .isInstanceOf(ConcurrencyConflict.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRequestCancelled.class::isInstance);
  }

  /** A second writer during the retry: Grandma answers once more and saves first. */
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
