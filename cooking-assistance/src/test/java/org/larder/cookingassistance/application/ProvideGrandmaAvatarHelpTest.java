package org.larder.cookingassistance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.application.fakes.InMemoryProcessedMessages;
import org.larder.cookingassistance.application.fakes.RecordingUnitOfWork;
import org.larder.cookingassistance.domain.helprequest.AnswerTooLate;
import org.larder.cookingassistance.domain.helprequest.CatastropheMitigation;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpProvided;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteRejectedBefore;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Handler for Grandma Avatar's answers from the broker (B3-S02, US-CA-03): Provide Help as the
 * service's own identity, idempotent by message id in one unit of work (ADR0015).
 */
class ProvideGrandmaAvatarHelpTest {

  static final UUID M1 = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final InMemoryProcessedMessages processedMessages = new InMemoryProcessedMessages();
  final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
  final ProvideGrandmaAvatarHelp handler =
      new ProvideGrandmaAvatarHelp(
          helpRequests,
          processedMessages,
          unitOfWork,
          () -> HelpRequests.HELP_ID.value(),
          HelpRequests.FRIDAY_18_10);

  /** One hour after Lena asked about the creme fraiche. */
  static final Clock MONDAY_19_00 = Clock.offset(HelpRequests.MONDAY_18_00, Duration.ofHours(1));

  final ProvideGrandmaAvatarHelp ingredientHandler =
      new ProvideGrandmaAvatarHelp(
          helpRequests,
          processedMessages,
          unitOfWork,
          () -> HelpRequests.OTHER_HELP_ID.value(),
          MONDAY_19_00);

  @BeforeEach
  void lenasRequestToGrandmaIsStored() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  static ProvideGrandmaAvatarHelp.GrandmaAnswer grandmasAnswer(UUID messageId) {
    return new ProvideGrandmaAvatarHelp.GrandmaAnswer(
        messageId,
        HelpRequests.HELP_REQUEST_ID,
        "Butter in a thin stream",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS);
  }

  @DisplayName(
      "Grandma's answer (message id m1) about step 4 -> Grandma's Help is added to Lena's request and saved with HelpProvided -- B3-S02 US-CA-03 cmd Provide Help (GrandmaHelpProvided)")
  @Test
  void grandmasAnswerIsAddedAsHelp() {
    handler.handle(grandmasAnswer(M1));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(
            help -> {
              assertThat(help.id()).isEqualTo(HelpRequests.HELP_ID);
              assertThat(help.providerType()).isEqualTo(HelpProviderType.GRANDMA_AVATAR);
            });
    assertThat(helpRequests.recordedEvents()).anyMatch(HelpProvided.class::isInstance);
  }

  @DisplayName(
      "the same message id m1 delivered twice -> only one Help -- B3-S02 US-CA-03 ADR0015 idempotency")
  @Test
  void redeliveredMessageHasNoEffect() {
    handler.handle(grandmasAnswer(M1));

    handler.handle(grandmasAnswer(M1));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
  }

  @DisplayName(
      "the processed-message record and the save happen inside one unit of work -- B3-S02 US-CA-03 ADR0015")
  @Test
  void recordAndSaveShareOneUnitOfWork() {
    AtomicBoolean recordedInUnitOfWork = new AtomicBoolean();
    AtomicBoolean savedInUnitOfWork = new AtomicBoolean();
    ProcessedMessages recordingSpy =
        messageId -> {
          recordedInUnitOfWork.set(unitOfWork.isActive());
          return processedMessages.recordFirstProcessing(messageId);
        };
    HelpRequestRepository savingSpy =
        new HelpRequestRepository() {
          @Override
          public Optional<HelpRequest> findById(HelpRequestId id) {
            return helpRequests.findById(id);
          }

          @Override
          public List<HelpRequest> findDueForTimeOut(Instant now, int limit) {
            return helpRequests.findDueForTimeOut(now, limit);
          }

          @Override
          public void save(HelpRequest helpRequest) {
            savedInUnitOfWork.set(unitOfWork.isActive());
            helpRequests.save(helpRequest);
          }
        };
    ProvideGrandmaAvatarHelp spiedHandler =
        new ProvideGrandmaAvatarHelp(
            savingSpy,
            recordingSpy,
            unitOfWork,
            () -> HelpRequests.HELP_ID.value(),
            HelpRequests.FRIDAY_18_10);

    spiedHandler.handle(grandmasAnswer(M1));

    assertThat(recordedInUnitOfWork).isTrue();
    assertThat(savedInUnitOfWork).isTrue();
  }

  @DisplayName(
      "another writer saves first (Jonas answers in between) -> the handler retries once in a new unit of work: no ConcurrencyConflict, message id m1 is looked up again, Jonas's Help is stored -- B3-S02 US-CA-03 ADR0010 ADR0015")
  @Test
  void conflictIsRetriedOnce() {
    // the fakes do not roll back the processed-message record (user 2026-11-07): the retry is shown
    // by the second lookup; rollback itself is proven on the real unit of work (UnitOfWorkIT)
    helpRequests.clear();
    helpRequests.save(
        HelpRequests.lenaAsksAboutStep4(
            HelpProviderType.GRANDMA_AVATAR, HelpProviderType.COMMUNITY));
    List<UUID> lookups = new ArrayList<>();
    ProcessedMessages lookupSpy =
        messageId -> {
          lookups.add(messageId);
          return processedMessages.recordFirstProcessing(messageId);
        };
    ProvideGrandmaAvatarHelp spiedHandler =
        new ProvideGrandmaAvatarHelp(
            helpRequests,
            lookupSpy,
            unitOfWork,
            () -> HelpRequests.HELP_ID.value(),
            HelpRequests.FRIDAY_18_10);
    helpRequests.interleave(this::jonasAnswersMeanwhile);

    spiedHandler.handle(grandmasAnswer(M1));

    assertThat(lookups).containsExactly(M1, M1);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .anyMatch(help -> help.provider().equals(Optional.of(HelpRequests.JONAS)));
  }

  /** Another writer: Jonas answers the same request and saves first. */
  private void jonasAnswersMeanwhile() {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(
        meanwhile, HelpRequests.OTHER_HELP_ID, HelpRequests.JONAS);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "unknown help request id -> HelpRequestNotFound propagates (dead-lettered by the consumer, step 9) -- B3-S02 US-CA-03 ADR0015")
  @Test
  void unknownHelpRequestPropagatesNotFound() {
    HelpRequestId unknown =
        new HelpRequestId(UUID.fromString("00000000-0000-7000-8000-000000000009"));

    assertThatThrownBy(
            () ->
                handler.handle(
                    new ProvideGrandmaAvatarHelp.GrandmaAnswer(
                        M1, unknown, "Butter", HelpRequests.BUTTER_WITH_THREE_PHOTOS)))
        .isInstanceOf(HelpRequestNotFound.class);
  }

  @DisplayName(
      "Grandma answers a CANCELLED request -> InvalidHelpRequestState propagates unchanged (named outcome: dead-lettered with its rule, step 9) -- B3-S02 US-CA-03 lifecycle")
  @Test
  void domainRejectionPropagatesUnchanged() {
    InMemoryHelpRequestRepository withCancelledRequest = new InMemoryHelpRequestRepository();
    HelpRequest open = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    withCancelledRequest.save(
        HelpRequest.reconstitute(
            open.id(),
            open.requester(),
            open.title(),
            open.description(),
            open.type(),
            open.recipe(),
            open.howToStep(),
            open.askedProviders(),
            HelpRequestStatus.CANCELLED,
            List.of(),
            List.of(),
            0L));
    ProvideGrandmaAvatarHelp cancelledHandler =
        new ProvideGrandmaAvatarHelp(
            withCancelledRequest,
            processedMessages,
            unitOfWork,
            () -> HelpRequests.HELP_ID.value(),
            HelpRequests.FRIDAY_18_10);

    assertThatThrownBy(() -> cancelledHandler.handle(grandmasAnswer(M1)))
        .isInstanceOf(InvalidHelpRequestState.class);
  }

  @DisplayName(
      "Grandma's substitutes (message id m1) -> her Help with the Substitutes is added to Lena's ingredient request and saved with HelpProvided -- B2-S02 US-CA-02 cmd Provide Help (GrandmaHelpProvided)")
  @Test
  void grandmasSubstitutesAreAddedAsHelp() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR));
    Substitutes yogurt =
        new Substitutes(HelpRequests.TOMATO_SOUP, List.of(HelpRequests.gramsOf("yogurt", "200")));

    ingredientHandler.handle(grandmaProposes(M1, yogurt));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            saved ->
                assertThat(saved.helps())
                    .singleElement()
                    .extracting(Help::answer)
                    .isEqualTo(yogurt));
    assertThat(helpRequests.recordedEvents()).last().isInstanceOf(HelpProvided.class);
  }

  @DisplayName(
      "Lena rejected yogurt; Grandma proposes yogurt -> SubstituteRejectedBefore propagates unchanged, no further Help saved -- B3-S05 US-CA-02 INV-15")
  @Test
  void rejectedSubstituteFromGrandmaPropagatesUnchanged() {
    helpRequests.clear();
    HelpRequest request = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    Substitute yogurt = HelpRequests.gramsOf("yogurt", "200");
    HelpRequests.grandmaProposes(request, yogurt);
    request.reject(HelpRequests.HELP_ID, "too sour", MONDAY_19_00);
    helpRequests.save(request);
    Substitutes yogurtAgain = new Substitutes(HelpRequests.TOMATO_SOUP, List.of(yogurt));

    assertThatThrownBy(() -> ingredientHandler.handle(grandmaProposes(M1, yogurtAgain)))
        .isInstanceOfSatisfying(
            SubstituteRejectedBefore.class,
            rejection -> assertThat(rejection.rule()).isEqualTo("INV-15"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
  }

  static final CatastropheMitigation SAVE_THE_CHICKEN =
      new CatastropheMitigation(
          HelpRequests.ROAST_CHICKEN,
          "Cut off the black skin and serve it with a sauce",
          List.of(
              URI.create("https://img.larder.example/cut-skin.jpg"),
              URI.create("https://img.larder.example/sauce.jpg")));

  /** The handler with its clock this long after Lena asked about her burnt chicken (Sat 18:40). */
  private ProvideGrandmaAvatarHelp catastropheHandler(Duration afterTheRequest) {
    return new ProvideGrandmaAvatarHelp(
        helpRequests,
        processedMessages,
        unitOfWork,
        () -> HelpRequests.OTHER_HELP_ID.value(),
        Clock.offset(HelpRequests.SATURDAY_18_40, afterTheRequest));
  }

  private static HelpRequest lenaAsksGrandmaAboutBurntChicken() {
    return HelpRequests.lenaAsksAboutBurntChicken(
        Optional.of(HelpRequests.ROAST_CHICKEN),
        HelpRequests.BURNT_CHICKEN_PHOTOS,
        HelpProviderType.GRANDMA_AVATAR);
  }

  private static ProvideGrandmaAvatarHelp.GrandmaAnswer grandmaMitigates(UUID messageId) {
    return new ProvideGrandmaAvatarHelp.GrandmaAnswer(
        messageId, HelpRequests.HELP_REQUEST_ID, "Save the chicken", SAVE_THE_CHICKEN);
  }

  private static ProvideGrandmaAvatarHelp.GrandmaAnswer grandmaProposes(
      UUID messageId, Substitutes answer) {
    return new ProvideGrandmaAvatarHelp.GrandmaAnswer(
        messageId, HelpRequests.HELP_REQUEST_ID, "Grandma's ideas", answer);
  }

  @DisplayName(
      "Grandma's mitigation (message id m1) at 18:44 -> her Help with the CatastropheMitigation is added to Lena's catastrophe request and saved with HelpProvided -- B4-S02 US-CA-04 cmd Provide Help (Grandma Avatar) R5.E1")
  @Test
  void grandmaMitigatesACatastrophe() {
    helpRequests.clear();
    helpRequests.save(lenaAsksGrandmaAboutBurntChicken());

    catastropheHandler(Duration.ofMinutes(4)).handle(grandmaMitigates(M1));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID))
        .hasValueSatisfying(
            saved ->
                assertThat(saved.helps())
                    .singleElement()
                    .extracting(Help::answer)
                    .isEqualTo(SAVE_THE_CHICKEN));
    assertThat(helpRequests.recordedEvents()).last().isInstanceOf(HelpProvided.class);
  }

  @DisplayName(
      "Grandma's side timed out at 18:46; her mitigation arrives at 18:47 -> AnswerTooLate propagates unchanged (named outcome: dead-lettered with its rule, step 9), no Help saved -- B4-S02 US-CA-04 INV-26 R9.E3 step 3 r21")
  @Test
  void grandmaAnswersAfterHerSideTimedOut() {
    helpRequests.clear();
    HelpRequest request =
        HelpRequests.lenaAsksAboutBurntChicken(
            Optional.of(HelpRequests.ROAST_CHICKEN),
            HelpRequests.BURNT_CHICKEN_PHOTOS,
            HelpProviderType.COMMUNITY,
            HelpProviderType.GRANDMA_AVATAR);
    request.timeOut(Clock.offset(HelpRequests.SATURDAY_18_40, Duration.ofMinutes(6)));
    helpRequests.save(request);

    assertThatThrownBy(() -> catastropheHandler(Duration.ofMinutes(7)).handle(grandmaMitigates(M1)))
        .isInstanceOfSatisfying(
            AnswerTooLate.class, rejection -> assertThat(rejection.rule()).isEqualTo("INV-09"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
  }
}
