package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.AcceptHelp;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.CancelHelpRequest;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.application.RejectHelp;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.application.Role;
import org.larder.cookingassistance.application.TimeOutDueHelpRequests;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Wiring of the application layer to the real adapters on PostgreSQL (step 8): one happy path per
 * application unit, taken from the Spring context.
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class ApplicationWiringIT {

  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository helpRequests;
  @Autowired RequestHelpWithPreparationStep requestHelp;
  @Autowired ProvideHelp provideHelp;
  @Autowired ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp;
  @Autowired CancelHelpRequest cancelHelpRequest;
  @Autowired AcceptHelp acceptHelp;
  @Autowired RejectHelp rejectHelp;
  @Autowired TimeOutDueHelpRequests timeOutDueHelpRequests;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  private int outboxRows(String eventType) {
    return jdbc.sql("select count(*) from outbox where event_type = :type")
        .param("type", eventType)
        .query(Integer.class)
        .single();
  }

  @DisplayName(
      "RequestHelpWithPreparationStep from the context: Lena (cook) requests help -> the request is stored in PostgreSQL with one HelpRequestCreated outbox row -- B3-S01 US-CA-03 wiring")
  @Test
  void requestHelpIsWired() {
    HelpRequestId id =
        requestHelp.execute(
            new RequestHelpWithPreparationStep.Command(
                new Actor(HelpRequests.LENA, Set.of(Role.COOK), false),
                "How do I keep the sauce from splitting?",
                "Step 4 of my Hollandaise keeps splitting",
                HelpRequests.HOLLANDAISE,
                HelpRequests.STEP_4,
                Set.of(HelpProviderType.GRANDMA_AVATAR)));

    assertThat(helpRequests.findById(id))
        .hasValueSatisfying(stored -> assertThat(stored.requester()).isEqualTo(HelpRequests.LENA));
    assertThat(outboxRows("HelpRequestCreated")).isEqualTo(1);
  }

  @DisplayName(
      "ProvideHelp from the context: Jonas (community) answers a stored Community request -> his Help is stored with a HelpProvided outbox row -- B3-S02 US-CA-03 wiring")
  @Test
  void provideHelpIsWired() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    provideHelp.execute(
        new ProvideHelp.Command(
            new Actor(HelpRequests.JONAS, Set.of(Role.COOK, Role.COMMUNITY), false),
            HelpProviderType.COMMUNITY,
            HelpRequests.HELP_REQUEST_ID,
            "Warm the bowl",
            HelpRequests.BUTTER_WITH_THREE_PHOTOS));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(help -> assertThat(help.provider()).contains(HelpRequests.JONAS));
    assertThat(outboxRows("HelpProvided")).isEqualTo(1);
  }

  @DisplayName(
      "ProvideGrandmaAvatarHelp from the context: Grandma's message delivered twice -> one Help stored, message id recorded in processed_message -- B3-S02 US-CA-03 ADR0015 wiring")
  @Test
  void grandmaAvatarHelpIsWired() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
    ProvideGrandmaAvatarHelp.GrandmaAnswer answer =
        new ProvideGrandmaAvatarHelp.GrandmaAnswer(
            MESSAGE_ID,
            HelpRequests.HELP_REQUEST_ID,
            "Butter in a thin stream",
            HelpRequests.BUTTER_WITH_THREE_PHOTOS);

    provideGrandmaAvatarHelp.handle(answer);
    provideGrandmaAvatarHelp.handle(answer);

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
    assertThat(
            jdbc.sql("select count(*) from processed_message where message_id = :id")
                .param("id", MESSAGE_ID)
                .query(Integer.class)
                .single())
        .isEqualTo(1);
  }

  static final UUID MESSAGE_ID = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");

  @DisplayName(
      "Lena cancels her stored request through CancelHelpRequest and the PostgreSQL repository -> stored CANCELLED, one HelpRequestCancelled outbox row -- B3-S03 US-CA-03 wiring")
  @Test
  void cancelThroughTheRealAdapter() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    cancelHelpRequest.execute(
        new CancelHelpRequest.Command(LENA, HelpRequests.HELP_REQUEST_ID, Optional.empty()));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(outboxRows("HelpRequestCancelled")).isEqualTo(1);
  }

  static final Actor LENA = new Actor(HelpRequests.LENA, Set.of(Role.COOK), false);

  @DisplayName(
      "Lena rejects Grandma's first Help and accepts the second through RejectHelp and AcceptHelp -> stored CLOSED, outbox rows HelpRejected then HelpAccepted -- B3-S04,B3-S05 US-CA-03 wiring")
  @Test
  void acceptAndRejectThroughTheRealAdapter() {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    helpRequests.save(request);

    rejectHelp.execute(
        new RejectHelp.Command(
            LENA, HelpRequests.HELP_REQUEST_ID, HelpRequests.HELP_ID, "warm water didn't help"));
    HelpRequest rejected = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    rejected.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        Clock.systemUTC());
    helpRequests.save(rejected);
    acceptHelp.execute(
        new AcceptHelp.Command(LENA, HelpRequests.HELP_REQUEST_ID, HelpRequests.OTHER_HELP_ID));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(
            jdbc.sql(
                    "select event_type from outbox where event_type in"
                        + " ('HelpRejected', 'HelpAccepted') order by id")
                .query(String.class)
                .list())
        .containsExactly("HelpRejected", "HelpAccepted");
  }

  @DisplayName(
      "a stored due request timed out by TimeOutDueHelpRequests on PostgreSQL -> stored CANCELLED, one HelpRequestTimedOut outbox row -- B3-S06 US-CA-03 ADR0021 wiring")
  @Test
  void timeOutThroughTheRealAdapter() {
    helpRequests.save(
        HelpRequest.requestHelpWithPreparationStep(
            HelpRequests.HELP_REQUEST_ID,
            HelpRequests.LENA,
            "How do I keep the sauce from splitting?",
            "Step 4 of my Hollandaise keeps splitting",
            HelpRequests.HOLLANDAISE,
            HelpRequests.STEP_4,
            Set.of(HelpProviderType.COMMUNITY),
            Clock.offset(Clock.systemUTC(), Duration.ofHours(-13))));

    int timedOut = timeOutDueHelpRequests.execute();

    assertThat(timedOut).isEqualTo(1);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(outboxRows("HelpRequestTimedOut")).isEqualTo(1);
  }
}
