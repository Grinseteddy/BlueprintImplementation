package org.larder.cookingassistance.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * An outbox row becomes the message the AsyncAPI spec promises: channel, stable message id,
 * headers, payload, validated against the spec before it is sent.
 */
class OutboxMessageMapperTest {

  static final UUID REQUEST = HelpRequests.HELP_REQUEST_ID.value();
  static final String HELP_REQUEST_CREATED = OutboxPayloads.helpRequestCreated(REQUEST);
  static final String HELP_PROVIDED = OutboxPayloads.helpProvided(REQUEST);

  final OutboxMessageMapper mapper = new OutboxMessageMapper();

  @DisplayName(
      "HelpRequestCreated row (id 42, aggregate = Lena's request) -> channel cooking-assistance.help-request.created, messageId = UUID v3 of \"cooking-assistance-outbox-42\", headers {messageId, correlationId = helpRequestId, source = cooking-assistance}, payload unchanged -- B3-S01 US-CA-03 op publishHelpRequestCreated")
  @Test
  void helpRequestCreatedRowBecomesItsMessage() {
    OutgoingMessage message =
        mapper.toMessage(new OutboxRecord(42, REQUEST, "HelpRequestCreated", HELP_REQUEST_CREATED));

    UUID messageId = UUID.fromString("8c6894b6-9321-3924-ac9a-3d6f6d4c5aa7");
    assertThat(message)
        .isEqualTo(
            new OutgoingMessage(
                "cooking-assistance.help-request.created",
                messageId,
                Map.of(
                    "messageId", messageId.toString(),
                    "correlationId", REQUEST.toString(),
                    "source", "cooking-assistance"),
                HELP_REQUEST_CREATED));
  }

  @DisplayName(
      "HelpProvided row -> channel cooking-assistance.help.provided, headers by the same rules, payload unchanged -- B3-S02 US-CA-03 op publishHelpProvided")
  @Test
  void helpProvidedRowBecomesItsMessage() {
    OutgoingMessage message =
        mapper.toMessage(new OutboxRecord(43, REQUEST, "HelpProvided", HELP_PROVIDED));

    UUID messageId = UUID.fromString("14020e3e-d27b-3db8-8fac-cc788859d6e0");
    assertThat(message)
        .isEqualTo(
            new OutgoingMessage(
                "cooking-assistance.help.provided",
                messageId,
                Map.of(
                    "messageId", messageId.toString(),
                    "correlationId", REQUEST.toString(),
                    "source", "cooking-assistance"),
                HELP_PROVIDED));
  }

  @DisplayName("the same row mapped twice -> the same messageId -- B3-S01 US-CA-03 ADR0015")
  @Test
  void sameRowKeepsItsMessageId() {
    OutboxRecord row = new OutboxRecord(42, REQUEST, "HelpRequestCreated", HELP_REQUEST_CREATED);

    assertThat(mapper.toMessage(row).messageId()).isEqualTo(mapper.toMessage(row).messageId());
  }

  @DisplayName(
      "the mapped message validates against the spec; with title removed -> InvalidOutgoingMessage -- B3-S01 US-CA-03 op publishHelpRequestCreated")
  @Test
  void messageBreakingTheSpecIsRefused() {
    String withoutTitle = HELP_REQUEST_CREATED.replace("\"title\":\"Sauce splits\",", "");

    assertThatThrownBy(
            () ->
                mapper.toMessage(new OutboxRecord(42, REQUEST, "HelpRequestCreated", withoutTitle)))
        .isInstanceOf(InvalidOutgoingMessage.class)
        .hasMessageContaining("title");
  }

  @DisplayName(
      "occurredAt \"yesterday\" -> InvalidOutgoingMessage (formats checked on the send side) -- B3-S01 US-CA-03 op publishHelpRequestCreated")
  @Test
  void brokenDateTimeIsRefused() {
    String yesterday =
        HELP_REQUEST_CREATED.replace(
            "\"occurredAt\":\"2026-11-06T18:00:00Z\"", "\"occurredAt\":\"yesterday\"");

    assertThatThrownBy(
            () -> mapper.toMessage(new OutboxRecord(42, REQUEST, "HelpRequestCreated", yesterday)))
        .isInstanceOf(InvalidOutgoingMessage.class);
  }

  @DisplayName(
      "event type the spec does not know (\"CopyBorrowed\") -> UnmappedEvent -- B3-S01 US-CA-03 ADR0015")
  @Test
  void unknownEventTypeIsUnmapped() {
    assertThatThrownBy(() -> mapper.toMessage(new OutboxRecord(44, REQUEST, "CopyBorrowed", "{}")))
        .isInstanceOf(UnmappedEvent.class);
  }

  @DisplayName(
      "the send operations of the spec = built (publishHelpRequestCreated, publishHelpProvided) + planned for increment 1 (publishHelpAccepted, publishHelpRejected, publishHelpRequestCancelled, publishHelpRequestTimedOut) -- B3-S01,B3-S02 US-CA-03 ADR0014")
  @Test
  void everySendOperationIsBuiltOrPlanned() throws Exception {
    Set<String> builtOrPlanned = new HashSet<>(SendOperations.BUILT);
    builtOrPlanned.addAll(SendOperations.PLANNED_FOR_INCREMENT_1);

    assertThat(builtOrPlanned).containsExactlyInAnyOrderElementsOf(sendOperationsOfTheSpec());
  }

  private static List<String> sendOperationsOfTheSpec() throws Exception {
    try (InputStream in =
        OutboxMessageMapperTest.class
            .getClassLoader()
            .getResourceAsStream("api/cooking-assistance.asyncapi.yaml")) {
      List<String> send = new ArrayList<>();
      new ObjectMapper(new YAMLFactory())
          .readTree(in)
          .get("operations")
          .fields()
          .forEachRemaining(
              operation -> {
                if ("send".equals(operation.getValue().path("action").asText())) {
                  send.add(operation.getKey());
                }
              });
      return send;
    }
  }
}
