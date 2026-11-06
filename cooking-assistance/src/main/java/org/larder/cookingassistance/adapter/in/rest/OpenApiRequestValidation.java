package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Validates request bodies against the approved OpenAPI spec (JSON Schema 2020-12, formats
 * asserted) before they are mapped.
 */
@ControllerAdvice
class OpenApiRequestValidation extends RequestBodyAdviceAdapter {

  // ADR0019: request bodies are limited to 1 MB
  private static final int MAX_BODY_BYTES = 1024 * 1024;
  private static final char NUL = 0;

  private final ObjectMapper json = new ObjectMapper();
  private final OpenApiContract contract;

  OpenApiRequestValidation(OpenApiContract contract) {
    this.contract = contract;
  }

  @Override
  public boolean supports(
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType) {
    return true;
  }

  @Override
  public HttpInputMessage beforeBodyRead(
      HttpInputMessage message,
      MethodParameter parameter,
      Type targetType,
      Class<? extends HttpMessageConverter<?>> converterType)
      throws IOException {
    byte[] body = readWithinSizeLimit(message);
    JsonNode parsed = parse(body);
    List<String> violations =
        new ArrayList<>(
            requestBodySchema(parameter.getMethod()).validate(parsed).stream()
                .map(ValidationMessage::getMessage)
                .toList());
    // PostgreSQL text cannot hold U+0000: refused at the edge, never a 500 from the database
    if (containsNul(parsed)) {
      violations.add("text must not contain U+0000");
    }
    if (!violations.isEmpty()) {
      throw new RequestValidationFailed(violations);
    }
    return new HttpInputMessage() {
      @Override
      public InputStream getBody() {
        return new ByteArrayInputStream(body);
      }

      @Override
      public HttpHeaders getHeaders() {
        return message.getHeaders();
      }
    };
  }

  /** A text value or a property name anywhere in the body; iterating a node visits its children. */
  private static boolean containsNul(JsonNode node) {
    if (node.isTextual()) {
      return node.textValue().indexOf(NUL) >= 0;
    }
    for (var field : node.properties()) {
      if (field.getKey().indexOf(NUL) >= 0) {
        return true;
      }
    }
    for (JsonNode child : node) {
      if (containsNul(child)) {
        return true;
      }
    }
    return false;
  }

  /**
   * ADR0019: a declared size over the limit is refused before a byte is read; a missing or false
   * Content-Length is caught by reading at most one byte more than the limit.
   */
  private static byte[] readWithinSizeLimit(HttpInputMessage message) throws IOException {
    if (message.getHeaders().getContentLength() > MAX_BODY_BYTES) {
      throw new RequestTooLarge();
    }
    byte[] body = message.getBody().readNBytes(MAX_BODY_BYTES + 1);
    if (body.length > MAX_BODY_BYTES) {
      throw new RequestTooLarge();
    }
    return body;
  }

  private JsonNode parse(byte[] body) {
    try {
      return json.readTree(body);
    } catch (IOException notJson) {
      throw new RequestValidationFailed(List.of("the body is not valid JSON"));
    }
  }

  private JsonSchema requestBodySchema(Method handler) {
    String requestBody =
        OpenApiContract.pointer(contract.operationServedBy(handler).get("requestBody"));
    return contract.schema(requestBody + "/content/application~1json/schema");
  }
}
