package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.fixtures.HelpRequests;

/** The wire format of the outbox payloads: exactly the AsyncAPI fields, no nulls (step 7 r2). */
class OutboxEventMapperTest {

  /** One second after the 12 h deadline of a request made Fri 18:00 (UTC+1). */
  private static final Clock SATURDAY_06_00_01 =
      Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC);

  @DisplayName(
      "HelpRequested -> payload with exactly helpRequestId, requester, title, type, description, recipe, howToStep, askedProviders, status, occurredAt and no null field -- B3-S01 US-CA-03 AsyncAPI HelpRequestCreated")
  @Test
  void helpRequestCreatedCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequestId",
            "requester",
            "title",
            "type",
            "description",
            "recipe",
            "howToStep",
            "askedProviders",
            "status",
            "occurredAt");
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpProvided by the Grandma Avatar -> payload with exactly helpId, helpRequest, helpRequester, answerTitle, helpProviderType, answer, helpRequestStatus, occurredAt (no helpProvider); answer with exactly answerType, recipe, howToStep, description, images -- B3-S02 US-CA-03 AsyncAPI HelpProvided")
  @Test
  void helpProvidedByGrandmaCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    request.pullEvents();
    HelpRequests.grandmaExplainsStep4(request);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpId",
            "helpRequest",
            "helpRequester",
            "answerTitle",
            "helpProviderType",
            "answer",
            "helpRequestStatus",
            "occurredAt");
    assertThat(fieldNames(payload.get("answer")))
        .containsExactlyInAnyOrder("answerType", "recipe", "howToStep", "description", "images");
    assertNoNulls(payload);
  }

  private static JsonNode payloadOf(HelpRequestEvent event) throws Exception {
    return new ObjectMapper().readTree(OutboxEventMapper.toOutboxEvent(event).payload());
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static void assertNoNulls(JsonNode node) {
    node.fields()
        .forEachRemaining(
            field -> {
              assertThat(field.getValue().isNull()).as(field.getKey()).isFalse();
              if (field.getValue().isObject()) {
                assertNoNulls(field.getValue());
              }
            });
  }

  @DisplayName(
      "HelpRequestCancelled with note 'an ice cube saved it' -> event_type HelpRequestCancelled, payload with exactly helpRequest, helpRequester, note, askedProviders[providerType, deadline], helpRequestStatus CANCELLED, occurredAt and no null field -- B3-S03 US-CA-03 INV-08 R7.E1 AsyncAPI HelpRequestCancelled")
  @Test
  void helpRequestCancelledCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    request.pullEvents();
    request.cancel(Optional.of("an ice cube saved it"), HelpRequests.FRIDAY_18_10);
    HelpRequestEvent cancelled = request.pullEvents().getFirst();

    JsonNode payload = payloadOf(cancelled);

    assertThat(OutboxEventMapper.toOutboxEvent(cancelled).eventType())
        .isEqualTo("HelpRequestCancelled");
    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequest",
            "helpRequester",
            "note",
            "askedProviders",
            "helpRequestStatus",
            "occurredAt");
    assertThat(fieldNames(payload.get("askedProviders").get(0)))
        .containsExactlyInAnyOrder("providerType", "deadline");
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpRequestCancelled without a note -> the payload has no note field (absent, not null) -- B3-S03 US-CA-03 INV-08 AsyncAPI HelpRequestCancelled (note optional)")
  @Test
  void helpRequestCancelledWithoutNoteHasNoNoteField() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    request.pullEvents();
    request.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(payload.has("note")).isFalse();
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpAccepted of Jonas's Help -> event_type HelpAccepted, payload with exactly helpRequest, helpRequester, helpId, helpProviderType COMMUNITY, helpProvider Jonas, helpRequestStatus CLOSED, occurredAt and no null field -- B3-S04 US-CA-03 INV-05 AsyncAPI HelpAccepted")
  @Test
  void helpAcceptedFromJonasCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    HelpRequests.communityMemberExplainsStep4(request, HelpRequests.HELP_ID, HelpRequests.JONAS);
    request.pullEvents();
    request.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);
    HelpRequestEvent accepted = request.pullEvents().getFirst();

    JsonNode payload = payloadOf(accepted);

    assertThat(OutboxEventMapper.toOutboxEvent(accepted).eventType()).isEqualTo("HelpAccepted");
    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequest",
            "helpRequester",
            "helpId",
            "helpProviderType",
            "helpProvider",
            "helpRequestStatus",
            "occurredAt");
    assertThat(payload.get("helpProviderType").asText()).isEqualTo("COMMUNITY");
    assertThat(payload.get("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("CLOSED");
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpAccepted of Grandma's Help -> helpProviderType GRANDMA_AVATAR and no helpProvider field -- B3-S04 US-CA-03 INV-05 AsyncAPI HelpAccepted")
  @Test
  void helpAcceptedFromGrandmaHasNoHelpProvider() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    request.pullEvents();
    request.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(payload.get("helpProviderType").asText()).isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.has("helpProvider")).isFalse();
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpRejected of Grandma's Help at Fri 20:00 with note 'warm water didn't help' -> event_type HelpRejected, payload with exactly helpRequest, helpRequester, helpId, note, askedProviders[GRANDMA_AVATAR, deadline 2026-11-07T07:00:00Z], helpRequestStatus OPEN, occurredAt and no null field -- B3-S05 US-CA-03 INV-06 AsyncAPI HelpRejected")
  @Test
  void helpRejectedCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    request.pullEvents();
    request.reject(
        HelpRequests.HELP_ID,
        "warm water didn't help",
        Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC));
    HelpRequestEvent rejected = request.pullEvents().getFirst();

    JsonNode payload = payloadOf(rejected);

    assertThat(OutboxEventMapper.toOutboxEvent(rejected).eventType()).isEqualTo("HelpRejected");
    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequest",
            "helpRequester",
            "helpId",
            "note",
            "askedProviders",
            "helpRequestStatus",
            "occurredAt");
    assertThat(payload.get("note").asText()).isEqualTo("warm water didn't help");
    assertThat(payload.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("GRANDMA_AVATAR");
    assertThat(payload.get("askedProviders").get(0).get("deadline").asText())
        .isEqualTo("2026-11-07T07:00:00Z");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("OPEN");
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpRequestTimedOut (only Community asked) -> event_type HelpRequestTimedOut, payload with exactly helpRequest, helpRequester, timedOutProviders [COMMUNITY], suggestedProviders [GRANDMA_AVATAR], helpRequestStatus CANCELLED, occurredAt and no null field -- B3-S06 US-CA-03 INV-09 R8.E1 AsyncAPI HelpRequestTimedOut")
  @Test
  void helpRequestTimedOutCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    request.pullEvents();
    request.timeOut(SATURDAY_06_00_01);
    HelpRequestEvent timedOut = request.pullEvents().getFirst();

    JsonNode payload = payloadOf(timedOut);

    assertThat(OutboxEventMapper.toOutboxEvent(timedOut).eventType())
        .isEqualTo("HelpRequestTimedOut");
    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequest",
            "helpRequester",
            "timedOutProviders",
            "suggestedProviders",
            "helpRequestStatus",
            "occurredAt");
    assertThat(payload.get("timedOutProviders").toString()).isEqualTo("[\"COMMUNITY\"]");
    assertThat(payload.get("suggestedProviders").toString()).isEqualTo("[\"GRANDMA_AVATAR\"]");
    assertThat(payload.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-07T05:00:01Z");
    assertNoNulls(payload);
  }

  @DisplayName(
      "HelpRequestTimedOut (Community and Grandma asked) -> suggestedProviders is an empty array [], not absent -- B3-S06 US-CA-03 INV-09 R8.E3 AsyncAPI HelpRequestTimedOut")
  @Test
  void helpRequestTimedOutWithNothingToSuggestWritesAnEmptyList() throws Exception {
    HelpRequest request =
        HelpRequests.lenaAsksAboutStep4(
            HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
    request.pullEvents();
    request.timeOut(SATURDAY_06_00_01);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(payload.has("suggestedProviders")).isTrue();
    assertThat(payload.get("suggestedProviders").toString()).isEqualTo("[]");
  }
}
