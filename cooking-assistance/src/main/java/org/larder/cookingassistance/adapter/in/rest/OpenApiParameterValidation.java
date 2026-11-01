package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.ValidationMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Validates the header parameters of an operation against the approved OpenAPI spec before the
 * handler runs; the generated interfaces declare defaults that would hide a missing header.
 */
class OpenApiParameterValidation implements HandlerInterceptor {

  private final ObjectMapper json = new ObjectMapper();
  private final OpenApiContract contract;

  OpenApiParameterValidation(OpenApiContract contract) {
    this.contract = contract;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (handler instanceof HandlerMethod method) {
      List<String> violations = new ArrayList<>();
      for (JsonNode reference : contract.operation(method.getMethod().getName()).path("parameters")) {
        String pointer = OpenApiContract.pointer(reference);
        JsonNode parameter = contract.at(pointer);
        if ("header".equals(parameter.path("in").asText())) {
          violations.addAll(
              validateHeader(pointer, parameter, request.getHeader(parameter.get("name").asText())));
        }
      }
      if (!violations.isEmpty()) {
        throw new RequestValidationFailed(violations);
      }
    }
    return true;
  }

  private List<String> validateHeader(String pointer, JsonNode parameter, String value) {
    if (value == null) {
      return parameter.path("required").asBoolean()
          ? List.of("header " + parameter.get("name").asText() + " is required")
          : List.of();
    }
    return contract.schema(pointer + "/schema").validate(quoted(value), InputFormat.JSON).stream()
        .map(ValidationMessage::getMessage)
        .toList();
  }

  private String quoted(String value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
