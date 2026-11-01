package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.fixtures.HelpRequests;

/** The wire format of the outbox payloads: exactly the AsyncAPI fields, no nulls (step 7 r2). */
class OutboxEventMapperTest {

  @DisplayName("HelpRequested -> payload with exactly helpRequestId, requester, title, type, description, recipe, howToStep, askedProviders, status, occurredAt and no null field -- B3-S01 US-CA-03 AsyncAPI HelpRequestCreated")
  @Test
  void helpRequestCreatedCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpRequestId", "requester", "title", "type", "description", "recipe", "howToStep",
            "askedProviders", "status", "occurredAt");
    assertNoNulls(payload);
  }

  @DisplayName("HelpProvided by the Grandma Avatar -> payload with exactly helpId, helpRequest, helpRequester, answerTitle, helpProviderType, answer, helpRequestStatus, occurredAt (no helpProvider); answer with exactly answerType, recipe, howToStep, description, images -- B3-S02 US-CA-03 AsyncAPI HelpProvided")
  @Test
  void helpProvidedByGrandmaCarriesExactlyTheSpecFields() throws Exception {
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    request.pullEvents();
    HelpRequests.grandmaExplainsStep4(request);

    JsonNode payload = payloadOf(request.pullEvents().getFirst());

    assertThat(fieldNames(payload))
        .containsExactlyInAnyOrder(
            "helpId", "helpRequest", "helpRequester", "answerTitle", "helpProviderType", "answer",
            "helpRequestStatus", "occurredAt");
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
}
