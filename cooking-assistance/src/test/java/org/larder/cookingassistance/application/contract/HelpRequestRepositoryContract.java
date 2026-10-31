package org.larder.cookingassistance.application.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
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

  @DisplayName("a saved request is found again equal (id, requester, title, step, asked providers, status, helps) with no pending events -- B3-S01 US-CA-03 contract")
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

  @DisplayName("saving a new request stores version 1; saving the loaded request again stores version 2 -- B3-S02 US-CA-03 ADR0010 contract")
  @Test
  void savingIncrementsTheVersionByOne() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(loaded.version()).isEqualTo(1L);

    repository().save(loaded);

    assertThat(repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().version())
        .isEqualTo(2L);
  }

  @DisplayName("the second of two concurrent saves of the same loaded version fails with ConcurrencyConflict -- B3-S02 US-CA-03 ADR0010 contract")
  @Test
  void secondConcurrentSaveFailsWithConcurrencyConflict() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    HelpRequest first = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequest second = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    repository().save(first);

    assertThatThrownBy(() -> repository().save(second)).isInstanceOf(ConcurrencyConflict.class);
  }

  @DisplayName("save records the pending events: 1 after a new request, 2 after Grandma's help is saved too -- B3-S01,B3-S02 US-CA-03 ADR0015 contract")
  @Test
  void saveRecordsThePendingEvents() {
    repository().save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    assertThat(recordedEventCount()).isEqualTo(1);

    HelpRequest loaded = repository().findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(loaded);
    repository().save(loaded);

    assertThat(recordedEventCount()).isEqualTo(2);
  }
}
