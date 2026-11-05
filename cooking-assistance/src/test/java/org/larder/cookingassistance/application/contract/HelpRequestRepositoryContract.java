package org.larder.cookingassistance.application.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.Note;
import org.larder.cookingassistance.domain.helprequest.NoteKind;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * The HelpRequestRepository contract (step 7), run against the in-memory fake (fast) and the
 * PostgreSQL adapter (slow), so the fake used by the use-case tests cannot drift from the real one.
 */
public abstract class HelpRequestRepositoryContract {

  /** The repository under test, empty at the start of each test. */
  protected abstract HelpRequestRepository repository();

  /** How many events the repository has recorded so far (outbox rows, or the fake's list). */
  protected abstract int recordedEventCount();

  @DisplayName("findById of an unknown id returns Optional.empty() -- B3-S01 US-CA-03 contract")
  @Test
  void unknownIdIsAbsent() {
    assertThat(
            repository()
                .findById(
                    new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000001"))))
        .isEmpty();
  }

  @DisplayName(
      "a saved request is found again equal (id, requester, title, step, asked providers, status, helps) with no pending events -- B3-S01 US-CA-03 contract")
  @Test
  void savedRequestIsFoundEqualWithoutPendingEvents() {
    HelpRequest saved = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);

    repository().save(saved);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            found -> {
              assertThat(found).isNotSameAs(saved);
              assertThat(found.id()).isEqualTo(saved.id());
              assertThat(found.requester()).isEqualTo(saved.requester());
              assertThat(found.title()).isEqualTo(saved.title());
              assertThat(found.howToStep()).isEqualTo(saved.howToStep());
              assertThat(found.askedProviders()).isEqualTo(saved.askedProviders());
              assertThat(found.status()).isEqualTo(saved.status());
              assertThat(found.helps()).isEmpty();
              assertThat(found.pullEvents()).isEmpty();
            });
  }

  @DisplayName(
      "saving a new request stores version 1; saving the loaded request again stores version 2 -- B3-S02 US-CA-03 ADR0010 contract")
  @Test
  void savingIncrementsTheVersionByOne() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(loaded.version()).isEqualTo(1L);

    repository().save(loaded);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().version())
        .isEqualTo(2L);
  }

  @DisplayName(
      "the second of two concurrent saves of the same loaded version fails with ConcurrencyConflict -- B3-S02 US-CA-03 ADR0010 contract")
  @Test
  void secondConcurrentSaveFailsWithConcurrencyConflict() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest first = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequest second = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    repository().save(first);

    assertThatThrownBy(() -> repository().save(second)).isInstanceOf(ConcurrencyConflict.class);
  }

  @DisplayName(
      "save records the pending events: 1 after a new request, 2 after Grandma's help is saved too -- B3-S01,B3-S02 US-CA-03 ADR0015 contract")
  @Test
  void saveRecordsThePendingEvents() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    assertThat(recordedEventCount()).isEqualTo(1);

    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(loaded);
    repository().save(loaded);

    assertThat(recordedEventCount()).isEqualTo(2);
  }

  @DisplayName(
      "a request whose Help Lena rejected at Fri 20:00 is found again OPEN with the Help rejected and not accepted, and Grandma's side ASKED with the new deadline Sat 08:00 -- B3-S05 US-CA-03 INV-06 INV-39 contract")
  @Test
  void rejectedHelpAndReaskedProvidersAreFoundAgain() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository().save(answered);
    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.reject(
        HelpRequests.HELP_ID,
        "warm water didn't help",
        Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC));

    repository().save(loaded);

    HelpRequest found = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(found.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(found.helps())
        .singleElement()
        .satisfies(
            help -> {
              assertThat(help.isRejected()).isTrue();
              assertThat(help.isAccepted()).isFalse();
            });
    assertThat(found.askedProviders())
        .containsExactly(
            new AskedProvider(
                HelpProviderType.GRANDMA_AVATAR,
                Instant.parse("2026-11-07T07:00:00Z"),
                ProviderState.ASKED));
  }

  @DisplayName(
      "Lena's request to the Community (deadline Sat 06:00), Open -> findDueForTimeOut(Sat 06:00:01) returns it -- B3-S06 US-CA-03 ADR0021 contract")
  @Test
  void openRequestPastEveryDeadlineIsDue() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    assertThat(repository().findDueForTimeOut(Instant.parse("2026-11-07T05:00:01Z"), 10))
        .extracting(HelpRequest::id)
        .containsExactly(HelpRequests.HELP_REQUEST_ID);
  }

  @DisplayName(
      "findDueForTimeOut(Sat 06:00:00), exactly the deadline -> nothing -- B3-S06 US-CA-03 INV-09 boundary contract")
  @Test
  void theDeadlineItselfIsNotDue() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    assertThat(repository().findDueForTimeOut(Instant.parse("2026-11-07T05:00:00Z"), 10)).isEmpty();
  }

  @DisplayName(
      "an ANSWERED, a CANCELLED and a CLOSED request past their deadlines -> nothing is due -- B3-S06 US-CA-03 D5 contract")
  @Test
  void onlyOpenRequestsAreDue() {
    HelpRequest answered = lenasRequestToGrandma(1);
    grandmaAnswers(answered, 1);
    repository().save(answered);
    HelpRequest cancelled = lenasRequestToGrandma(2);
    cancelled.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);
    repository().save(cancelled);
    HelpRequest closed = lenasRequestToGrandma(3);
    grandmaAnswers(closed, 3);
    closed.accept(helpId(3), HelpRequests.FRIDAY_18_10);
    repository().save(closed);

    assertThat(repository().findDueForTimeOut(Instant.parse("2026-11-07T05:00:01Z"), 10)).isEmpty();
  }

  /** Lena asks Grandma about step 4 on Friday 18:00, as request number {@code n}. */
  private static HelpRequest lenasRequestToGrandma(int n) {
    return HelpRequest.requestHelpWithPreparationStep(
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-00000000010" + n)),
        HelpRequests.LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HelpRequests.HOLLANDAISE,
        HelpRequests.STEP_4,
        Set.of(HelpProviderType.GRANDMA_AVATAR),
        HelpRequests.FRIDAY_18_00);
  }

  private static void grandmaAnswers(HelpRequest request, int n) {
    request.provideHelp(
        helpId(n),
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);
  }

  private static HelpId helpId(int n) {
    return new HelpId(UUID.fromString("00000000-0000-7000-8000-00000000020" + n));
  }

  @DisplayName(
      "Community's deadline Sat 06:00 passed, Grandma asked again until Sat 08:00 -> not due at Sat 07:00 -- B3-S06 US-CA-03 INV-09 per-provider contract")
  @Test
  void aRequestWithARunningSideIsNotDue() {
    repository()
        .save(
            HelpRequest.reconstitute(
                HelpRequests.HELP_REQUEST_ID,
                HelpRequests.LENA,
                "How do I keep the sauce from splitting?",
                "Step 4 of my Hollandaise keeps splitting",
                HelpType.PREPARATION_STEP_EXPLANATION,
                HelpRequests.HOLLANDAISE,
                HelpRequests.STEP_4,
                List.of(
                    new AskedProvider(
                        HelpProviderType.COMMUNITY,
                        Instant.parse("2026-11-07T05:00:00Z"),
                        ProviderState.ASKED),
                    new AskedProvider(
                        HelpProviderType.GRANDMA_AVATAR,
                        Instant.parse("2026-11-07T07:00:00Z"),
                        ProviderState.ASKED)),
                HelpRequestStatus.OPEN,
                List.of(),
                List.of(),
                0L));

    assertThat(repository().findDueForTimeOut(Instant.parse("2026-11-07T06:00:00Z"), 10)).isEmpty();
  }

  @DisplayName(
      "three due requests, limit 2 -> two are returned -- B3-S06 US-CA-03 ADR0021 batch contract")
  @Test
  void atMostTheLimitIsReturned() {
    repository().save(lenasRequestToGrandma(1));
    repository().save(lenasRequestToGrandma(2));
    repository().save(lenasRequestToGrandma(3));

    assertThat(repository().findDueForTimeOut(Instant.parse("2026-11-07T05:00:01Z"), 2)).hasSize(2);
  }

  @DisplayName(
      "K6 Lena cancelled at Fri 18:10 with 'an ice cube saved it' -> found again with notes [CANCELLATION 'an ice cube saved it' Fri 18:10] -- B3-S03 US-CA-03 INV-08 R7.E1 INV-07 contract (step 7 r1 of increment 1)")
  @Test
  void cancellationNoteIsFoundAgain() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.cancel(Optional.of("an ice cube saved it"), HelpRequests.FRIDAY_18_10);

    repository().save(loaded);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().notes())
        .containsExactly(
            new Note(
                NoteKind.CANCELLATION,
                "an ice cube saved it",
                HelpRequests.FRIDAY_18_10.instant(),
                Optional.empty()));
  }

  @DisplayName(
      "K7 a request with the note [REJECTION 'warm water didn't help' Fri 20:00], saved again after Grandma answered again -> found with that one note, not doubled -- B3-S05 US-CA-03 INV-06 INV-07 contract (step 7 r1 of increment 1)")
  @Test
  void notesAreNotDoubledBySavingAgain() {
    Clock friday2000 = Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC);
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository().save(answered);
    HelpRequest rejected = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    rejected.reject(HelpRequests.HELP_ID, "warm water didn't help", friday2000);
    repository().save(rejected);
    HelpRequest answeredAgain = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    answeredAgain.provideHelp(
        new HelpId(UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f81")),
        HelpProvider.grandmaAvatar(),
        "Cool the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        Clock.fixed(Instant.parse("2026-11-06T19:30:00Z"), ZoneOffset.UTC));

    repository().save(answeredAgain);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().notes())
        .containsExactly(
            new Note(
                NoteKind.REJECTION,
                "warm water didn't help",
                friday2000.instant(),
                Optional.of(HelpRequests.HELP_ID)));
  }

  @DisplayName(
      "K8 Lena rejected Grandma's Help (HELP_ID) with 'warm water didn't help' -> found again with the note naming HELP_ID -- B3-S05 US-CA-03 INV-06 contract (step 7 r1 of increment 1, added: F5)")
  @Test
  void rejectionNoteKeepsItsHelp() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest answered = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(answered);
    repository().save(answered);
    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    loaded.reject(
        HelpRequests.HELP_ID,
        "warm water didn't help",
        Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC));

    repository().save(loaded);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().notes())
        .extracting(Note::help)
        .containsExactly(Optional.of(HelpRequests.HELP_ID));
  }
}
