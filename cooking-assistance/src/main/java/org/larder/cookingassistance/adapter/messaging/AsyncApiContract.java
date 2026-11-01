package org.larder.cookingassistance.adapter.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The approved AsyncAPI spec as consumers and the relay read it: component schemas (JSON Schema draft-07,
 * the base of the AsyncAPI schema format; formats asserted).
 */
public final class AsyncApiContract {

  private static final String SPEC = "api/cooking-assistance.asyncapi.yaml";

  private final JsonNode spec = readSpec();
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private final SchemaValidatorsConfig config =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

  /** The address of the channel that carries the message with this name. */
  public Optional<String> channelAddressOf(String messageName) {
    for (JsonNode channel : spec.get("channels")) {
      for (JsonNode message : channel.path("messages")) {
        if (messageName.equals(spec.at(pointer(message)).path("name").asText())) {
          return Optional.of(channel.get("address").asText());
        }
      }
    }
    return Optional.empty();
  }

  /** The name of the component schema of the payload of the message with this name. */
  public Optional<String> payloadSchemaOf(String messageName) {
    for (JsonNode message : spec.path("components").path("messages")) {
      if (messageName.equals(message.path("name").asText())) {
        String payload = pointer(message.get("payload"));
        return Optional.of(payload.substring(payload.lastIndexOf('/') + 1));
      }
    }
    return Optional.empty();
  }

  private static String pointer(JsonNode reference) {
    return reference.get("$ref").asText().substring(1);
  }

  /** The violations of a node against a component schema; empty if it matches. */
  public List<String> violations(String schemaName, JsonNode node) {
    return schema(schemaName).validate(node).stream().map(ValidationMessage::getMessage).toList();
  }

  private JsonSchema schema(String schemaName) {
    return schemas.computeIfAbsent(
        schemaName,
        name ->
            factory.getSchema(
                SchemaLocation.of("classpath:" + SPEC + "#/components/schemas/" + name), config));
  }

  private static JsonNode readSpec() {
    try (InputStream in = AsyncApiContract.class.getClassLoader().getResourceAsStream(SPEC)) {
      return new ObjectMapper(new YAMLFactory()).readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
