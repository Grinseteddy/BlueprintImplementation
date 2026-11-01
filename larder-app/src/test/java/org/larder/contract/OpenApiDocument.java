package org.larder.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.oas.OpenApi31;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/** The approved OpenAPI spec on the classpath: its documented examples and its schemas. */
final class OpenApiDocument {

  private static final String SPEC = "api/cooking-assistance.openapi.yaml";

  private final JsonNode spec = read();
  private final JsonSchemaFactory factory =
      JsonSchemaFactory.getInstance(
          SpecVersion.VersionFlag.V202012,
          builder ->
              builder
                  .metaSchema(OpenApi31.getInstance())
                  .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));
  private final SchemaValidatorsConfig config =
      SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();

  /** The example of a request body, as JSON text. */
  String requestBodyExample(String requestBody, String example) {
    return spec.at("/components/requestBodies/" + requestBody + "/content/application~1json/examples/" + example + "/value")
        .toString();
  }

  List<String> violations(String schemaName, String json) {
    return factory
        .getSchema(SchemaLocation.of("classpath:" + SPEC + "#/components/schemas/" + schemaName), config)
        .validate(json, InputFormat.JSON)
        .stream()
        .map(ValidationMessage::getMessage)
        .toList();
  }

  private static JsonNode read() {
    try (InputStream in = OpenApiDocument.class.getClassLoader().getResourceAsStream(SPEC)) {
      return new ObjectMapper(new YAMLFactory()).readTree(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
