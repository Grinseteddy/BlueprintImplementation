package org.larder.cookingassistance.adapter.out.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.larder.cookingassistance.adapter.messaging.AsyncApiContract;

/** Turns outbox rows into the messages the AsyncAPI spec promises. */
class OutboxMessageMapper {

  private static final String SOURCE = "cooking-assistance";

  private final ObjectMapper json = new ObjectMapper();
  private final AsyncApiContract contract = new AsyncApiContract();

  OutgoingMessage toMessage(OutboxRecord row) {
    UUID messageId = messageIdOf(row);
    OutgoingMessage message =
        new OutgoingMessage(
            contract.channelAddressOf(row.eventType()).orElseThrow(() -> unmapped(row)),
            messageId,
            headersOf(row, messageId),
            row.payload());
    checkAgainstSpec(row, message);
    return message;
  }

  /** ADR0015: the message keeps its promise to consumers or is never sent. */
  private void checkAgainstSpec(OutboxRecord row, OutgoingMessage message) {
    validate("MessageHeader", json.valueToTree(message.headers()));
    validate(
        contract.payloadSchemaOf(row.eventType()).orElseThrow(() -> unmapped(row)),
        parse(message.payload()));
  }

  /** The MessageHeader of the spec. */
  private static Map<String, String> headersOf(OutboxRecord row, UUID messageId) {
    return Map.of(
        "messageId", messageId.toString(),
        // one help journey is one help request until correlation is propagated (L8, step 12)
        "correlationId", row.aggregateId().toString(),
        "source", SOURCE);
  }

  private static UnmappedEvent unmapped(OutboxRecord row) {
    return new UnmappedEvent(row.eventType());
  }

  private void validate(String schemaName, JsonNode node) {
    List<String> violations = contract.violations(schemaName, node);
    if (!violations.isEmpty()) {
      throw new InvalidOutgoingMessage(violations);
    }
  }

  private JsonNode parse(String payload) {
    try {
      return json.readTree(payload);
    } catch (IOException notJson) {
      throw new InvalidOutgoingMessage(List.of("the payload is not valid JSON"));
    }
  }

  /** ADR0015: stable across retries and relays, so consumers drop redeliveries. */
  private static UUID messageIdOf(OutboxRecord row) {
    return UUID.nameUUIDFromBytes(
        ("cooking-assistance-outbox-" + row.id()).getBytes(StandardCharsets.UTF_8));
  }
}
