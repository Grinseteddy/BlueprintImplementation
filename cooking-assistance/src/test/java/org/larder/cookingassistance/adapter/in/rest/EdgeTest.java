package org.larder.cookingassistance.adapter.in.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * What the whole API shares: undeclared paths, methods and content types, unparsable bodies -> Problem.
 */
class EdgeTest {

  @Disabled("step 9 test list")
  @DisplayName("GET /recipes -> 404 NOT_FOUND Problem -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredPathIsNotFound() {}

  @Disabled("step 9 test list")
  @DisplayName("DELETE /help-requests -> 405 METHOD_NOT_ALLOWED Problem with Allow: GET, POST -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredMethodIsNotAllowed() {}

  @Disabled("step 9 test list")
  @DisplayName("POST /help-requests as text/plain -> 415 UNSUPPORTED_MEDIA_TYPE Problem -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredContentTypeIsUnsupported() {}

  @Disabled("step 9 test list")
  @DisplayName("POST /help-requests with malformed JSON -> 400 VALIDATION_FAILED Problem -- B3-S01 US-CA-03 edge")
  @Test
  void malformedJsonIsABadRequest() {}
}
