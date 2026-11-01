package org.larder.cookingassistance.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The approved AsyncAPI spec as the consumers read it: component schemas (JSON Schema draft-07,
 * the base of the AsyncAPI schema format; formats asserted).
 */
final class AsyncApiContract {

  private static final String SPEC = "api/cooking-assistance.asyncapi.yaml";

  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
  private final SchemaValidatorsConfig config =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

  /** The violations of a node against a component schema; empty if it matches. */
  List<String> violations(String schemaName, JsonNode node) {
    return schema(schemaName).validate(node).stream().map(ValidationMessage::getMessage).toList();
  }

  private JsonSchema schema(String schemaName) {
    return schemas.computeIfAbsent(
        schemaName,
        name ->
            factory.getSchema(
                SchemaLocation.of("classpath:" + SPEC + "#/components/schemas/" + name), config));
  }
}
