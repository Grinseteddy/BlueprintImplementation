package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.application.fakes.RecordingUnitOfWork;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.DeadlineNotPassed;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpRequestTimedOut;
import org.larder.cookingassistance.domain.helprequest.ProviderState;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Use case Time out help request (B3-S06, US-CA-03): the clock times out due requests (ADR0021).
 */
class TimeOutDueHelpRequestsTest {

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();

  @BeforeEach
  void lenasRequestToTheCommunityIsStored() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
  }

  TimeOutDueHelpRequests useCaseAt(String instant) {
    return new TimeOutDueHelpRequests(
        helpRequests, unitOfWork, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC), 10);
  }

  @DisplayName(
      "Lena's request to the Community, silent; run at Sat 06:00:01 -> the saved request is CANCELLED with Community's side TIMED_OUT -- B3-S06 US-CA-03 cmd Time out help request ADR0021")
  @Test
  void aDueRequestIsTimedOut() {
    useCaseAt("2026-11-07T05:00:01Z").execute();

    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(stored.askedProviders())
        .extracting(AskedProvider::state)
        .containsExactly(ProviderState.TIMED_OUT);
  }

  @DisplayName(
      "the HelpRequestTimedOut event is saved with the request -- B3-S06 US-CA-03 evt HelpRequestTimedOut")
  @Test
  void theTimedOutEventIsSavedWithTheRequest() {
    useCaseAt("2026-11-07T05:00:01Z").execute();

    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRequestTimedOut.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestTimedOut.class,
            event ->
                assertThat(event.timedOutProviders()).containsExactly(HelpProviderType.COMMUNITY));
  }

  @DisplayName(
      "run at Sat 06:00:00 (exactly the deadline) -> no request is due, nothing saved, 0 returned -- B3-S06 US-CA-03 INV-09 boundary")
  @Test
  void nothingDueSavesNothing() {
    int timedOut = useCaseAt("2026-11-07T05:00:00Z").execute();

    assertThat(timedOut).isZero();
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRequestTimedOut.class::isInstance);
  }

  @DisplayName(
      "two due requests (Lena's and Tom's) and one answered -> both due ones CANCELLED, the answered one unchanged, 2 returned -- B3-S06 US-CA-03 ADR0021")
  @Test
  void everyDueRequestIsTimedOutAndCounted() {
    helpRequests.save(requestToTheCommunity(TOMS_REQUEST, CancelHelpRequestTest.TOM.id()));
    HelpRequest answered = requestToTheCommunity(ANSWERED_REQUEST, HelpRequests.LENA);
    HelpRequests.communityMemberExplainsStep4(answered, HelpRequests.HELP_ID, HelpRequests.JONAS);
    helpRequests.save(answered);

    int timedOut = useCaseAt("2026-11-07T05:00:01Z").execute();

    assertThat(timedOut).isEqualTo(2);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(helpRequests.findById(TOMS_REQUEST).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(helpRequests.findById(ANSWERED_REQUEST).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
  }

  static final HelpRequestId TOMS_REQUEST =
      new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000301"));
  static final HelpRequestId ANSWERED_REQUEST =
      new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000302"));

  /** A request about step 4 to the Community on Friday 18:00 (deadline Sat 06:00). */
  private static HelpRequest requestToTheCommunity(HelpRequestId id, CookId requester) {
    return HelpRequest.requestHelpWithPreparationStep(
        id,
        requester,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HelpRequests.HOLLANDAISE,
        HelpRequests.STEP_4,
        Set.of(HelpProviderType.COMMUNITY),
        HelpRequests.FRIDAY_18_00);
  }

  @DisplayName(
      "the due requests are found and saved inside one unit of work (the claim's row locks hold until the saves commit) -- B3-S06 US-CA-03 ADR0021 QS-09")
  @Test
  void claimAndSaveRunInOneUnitOfWork() {
    AtomicBoolean claimedInUnitOfWork = new AtomicBoolean();
    AtomicBoolean savedInUnitOfWork = new AtomicBoolean();
    HelpRequestRepository spy =
        new HelpRequestRepository() {
          @Override
          public Optional<HelpRequest> findById(HelpRequestId id) {
            return helpRequests.findById(id);
          }

          @Override
          public List<HelpRequest> findDueForTimeOut(Instant now, int limit) {
            claimedInUnitOfWork.set(unitOfWork.isActive());
            return helpRequests.findDueForTimeOut(now, limit);
          }

          @Override
          public void save(HelpRequest helpRequest) {
            savedInUnitOfWork.set(unitOfWork.isActive());
            helpRequests.save(helpRequest);
          }
        };

    new TimeOutDueHelpRequests(
            spy, unitOfWork, Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC), 10)
        .execute();

    assertThat(claimedInUnitOfWork).isTrue();
    assertThat(savedInUnitOfWork).isTrue();
  }

  @DisplayName(
      "the repository hands over a request whose deadline has not passed -> DeadlineNotPassed propagates (a defect of the claim query, ADR0021), nothing saved -- B3-S06 US-CA-03 INV-09")
  @Test
  void aClaimedRequestThatIsNotDuePropagates() {
    HelpRequestRepository tooGenerous =
        new HelpRequestRepository() {
          @Override
          public Optional<HelpRequest> findById(HelpRequestId id) {
            return helpRequests.findById(id);
          }

          @Override
          public List<HelpRequest> findDueForTimeOut(Instant now, int limit) {
            return List.of(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow());
          }

          @Override
          public void save(HelpRequest helpRequest) {
            helpRequests.save(helpRequest);
          }
        };
    TimeOutDueHelpRequests atTheDeadline =
        new TimeOutDueHelpRequests(
            tooGenerous,
            unitOfWork,
            Clock.fixed(Instant.parse("2026-11-07T05:00:00Z"), ZoneOffset.UTC),
            10);

    assertThatThrownBy(atTheDeadline::execute).isInstanceOf(DeadlineNotPassed.class);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
    assertThat(helpRequests.recordedEvents()).noneMatch(HelpRequestTimedOut.class::isInstance);
  }
}
