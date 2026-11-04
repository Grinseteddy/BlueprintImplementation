package org.larder.cookingassistance.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.TimeOutDueHelpRequests;
import org.larder.cookingassistance.application.UnitOfWork;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The time-out poller (ADR0021) around TimeOutDueHelpRequests, with the fake repository. */
class TimeOutPollingTest {

  /** Sat 06:00:01, one second after the Community's deadline of a Friday 18:00 request. */
  static final Clock SATURDAY_06_00_01 =
      Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC);

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  int runs;

  /** Counts each run of the use case: one unit of work per run. */
  final UnitOfWork countingUnitOfWork =
      new UnitOfWork() {
        @Override
        public <T> T transactionally(Supplier<T> work) {
          runs++;
          return work.get();
        }
      };

  TimeOutPolling pollerWithBatchSize(int batchSize) {
    return new TimeOutPolling(
        new TimeOutDueHelpRequests(helpRequests, countingUnitOfWork, SATURDAY_06_00_01, batchSize));
  }

  /** Stores n silent requests to the Community from Friday 18:00 (deadline Sat 06:00). */
  List<HelpRequestId> dueRequests(int n) {
    return IntStream.rangeClosed(1, n)
        .mapToObj(
            i -> {
              HelpRequestId id =
                  new HelpRequestId(
                      UUID.fromString("00000000-0000-7000-8000-%012d".formatted(400 + i)));
              helpRequests.save(
                  HelpRequest.requestHelpWithPreparationStep(
                      id,
                      HelpRequests.LENA,
                      "How do I keep the sauce from splitting?",
                      "Step 4 of my Hollandaise keeps splitting",
                      HelpRequests.HOLLANDAISE,
                      HelpRequests.STEP_4,
                      Set.of(HelpProviderType.COMMUNITY),
                      HelpRequests.FRIDAY_18_00));
              return id;
            })
        .toList();
  }

  void assertAllCancelled(List<HelpRequestId> ids) {
    assertThat(ids)
        .allSatisfy(
            id ->
                assertThat(helpRequests.findById(id).orElseThrow().status())
                    .isEqualTo(HelpRequestStatus.CANCELLED));
  }

  @DisplayName(
      "S1 one poll, 3 due requests (batch size 100) -> the use case runs once, all 3 CANCELLED -- B3-S06 US-CA-03 ADR0021")
  @Test
  void onePollTimesOutWhatIsDue() {
    List<HelpRequestId> due = dueRequests(3);

    pollerWithBatchSize(100).poll();

    assertThat(runs).isEqualTo(1);
    assertAllCancelled(due);
  }

  @DisplayName(
      "S2 one poll, 5 due requests with batch size 2 -> batches of 2, 2, 1 in the same poll, all 5 CANCELLED -- B3-S06 US-CA-03 ADR0021 QS-09 drain")
  @Test
  void onePollDrainsFullBatches() {
    List<HelpRequestId> due = dueRequests(5);

    pollerWithBatchSize(2).poll();

    assertThat(runs).isEqualTo(3);
    assertAllCancelled(due);
  }

  @DisplayName(
      "S3 one poll, nothing due -> the use case runs once and returns 0, nothing saved -- B3-S06 US-CA-03 ADR0021")
  @Test
  void onePollWithNothingDue() {
    pollerWithBatchSize(100).poll();

    assertThat(runs).isEqualTo(1);
    assertThat(helpRequests.recordedEvents()).isEmpty();
  }

  @DisplayName(
      "S4 cooking-assistance.timeout.poller.enabled=false -> no poller bean; unset -> the poller bean exists -- B3-S06 US-CA-03 ADR0021")
  @Test
  void pollerCanBeSwitchedOff() {
    ApplicationContextRunner context =
        new ApplicationContextRunner()
            .withUserConfiguration(TimeOutPolling.class)
            .withBean(
                TimeOutDueHelpRequests.class,
                () ->
                    new TimeOutDueHelpRequests(
                        helpRequests, countingUnitOfWork, SATURDAY_06_00_01, 100));

    context
        .withPropertyValues("cooking-assistance.timeout.poller.enabled=false")
        .run(switchedOff -> assertThat(switchedOff).doesNotHaveBean(TimeOutPolling.class));
    context.run(unset -> assertThat(unset).hasSingleBean(TimeOutPolling.class));
  }
}
