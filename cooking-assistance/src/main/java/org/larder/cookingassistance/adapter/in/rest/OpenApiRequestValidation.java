package org.larder.cookingassistance.adapter.in.rest;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Validates request bodies against the approved OpenAPI spec (JSON Schema 2020-12, formats
 * asserted) before they are mapped; the handler method's name is the operationId.
 */
@ControllerAdvice
class OpenApiRequestValidation extends RequestBodyAdviceAdapter {

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
    byte[] body = message.getBody().readAllBytes();
    List<String> violations =
        requestBodySchema(parameter.getMethod().getName())
            .validate(new String(body, StandardCharsets.UTF_8), InputFormat.JSON)
            .stream()
            .map(ValidationMessage::getMessage)
            .toList();
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

  private JsonSchema requestBodySchema(String operationId) {
    String requestBody = OpenApiContract.pointer(contract.operation(operationId).get("requestBody"));
    return contract.schema(requestBody + "/content/application~1json/schema");
  }
}
