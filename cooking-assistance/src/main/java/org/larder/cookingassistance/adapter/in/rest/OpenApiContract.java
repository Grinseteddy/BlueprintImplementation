package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.oas.OpenApi31;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The approved OpenAPI spec as the edge reads it: operations by operationId and their schemas
 * (JSON Schema 2020-12 with the OpenAPI 3.1 vocabulary, formats asserted).
 */
final class OpenApiContract {

  private static final String SPEC = "api/cooking-assistance.openapi.yaml";

  private final JsonNode spec = readSpec();
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(
          SpecVersion.VersionFlag.V202012,
          builder ->
              builder
                  .metaSchema(OpenApi31.getInstance())
                  .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));
  private final SchemaValidatorsConfig config =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();

  /**
   * The operation a handler serves; the generated interfaces name each method after its
   * operationId.
   */
  JsonNode operationServedBy(Method handler) {
    String operationId = handler.getName();
    for (JsonNode path : spec.get("paths")) {
      for (JsonNode operation : path) {
        if (operationId.equals(operation.path("operationId").asText())) {
          return operation;
        }
      }
    }
    throw new IllegalStateException("operation " + operationId + " is not in the spec");
  }

  /** The JSON pointer a local reference ({@code #/components/...}) points to. */
  static String pointer(JsonNode reference) {
    return reference.get("$ref").asText().substring(1);
  }

  JsonNode at(String pointer) {
    return spec.at(pointer);
  }

  JsonSchema schema(String pointer) {
    return schemas.computeIfAbsent(
        pointer,
        p -> factory.getSchema(SchemaLocation.of("classpath:" + SPEC + "#" + p), config));
  }

  private static JsonNode readSpec() {
    try (InputStream in = OpenApiContract.class.getClassLoader().getResourceAsStream(SPEC)) {
      return new ObjectMapper(new YAMLFactory()).readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
