package org.larder.cookingassistance.adapter.in.messaging;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import org.larder.cookingassistance.adapter.messaging.AsyncApiContract;

/**
 * Reads consumed messages as the approved AsyncAPI spec defines them: validated against its
 * component schemas before they are mapped, and read tolerantly (unknown fields are ignored).
 */
final class AsyncApiMessageReader {

  // AsyncAPI: consumers are tolerant readers and ignore unknown fields
  private final ObjectMapper json =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private final AsyncApiContract contract = new AsyncApiContract();

  /** The headers, validated against the spec's MessageHeader schema. */
  void validateHeaders(Map<String, Object> headers) {
    validate("MessageHeader", json.valueToTree(headers));
  }

  /** The payload, validated against its component schema before it is mapped. */
  <T> T readPayload(byte[] body, String schemaName, Class<T> payloadType) {
    JsonNode payload = parse(body);
    validate(schemaName, payload);
    try {
      return json.treeToValue(payload, payloadType);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void validate(String schemaName, JsonNode node) {
    List<String> violations = contract.violations(schemaName, node);
    if (!violations.isEmpty()) {
      throw new InvalidMessage(violations);
    }
  }

  private JsonNode parse(byte[] body) {
    try {
      return json.readTree(body);
    } catch (IOException notJson) {
      throw new InvalidMessage(List.of("the payload is not valid JSON"));
    }
  }
}
