package org.larder.cookingassistance.adapter.in.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The error map (OpenAPI 1.0.2 Problem codes): each domain rejection -> status, code and rule.
 */
class ProblemMapperTest {

  @Disabled("step 9 test list")
  @DisplayName("each of the 9 domain rejections -> its status (OWN_REQUEST, PROVIDER_NOT_ASKED 403; INVALID_STATE 409; the rest 422), its code and its rule -- B3-S01,B3-S02 US-CA-03 op createHelpRequest,provideHelp")
  @Test
  void domainRejectionsMapToStatusCodeAndRule() {}
}
