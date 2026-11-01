package org.larder.cookingassistance.adapter.in.rest;

import java.util.List;

/** The request does not match the approved OpenAPI spec; answered with 400 VALIDATION_FAILED. */
final class RequestValidationFailed extends RuntimeException {

  RequestValidationFailed(List<String> violations) {
    super(String.join("; ", violations));
  }
}
