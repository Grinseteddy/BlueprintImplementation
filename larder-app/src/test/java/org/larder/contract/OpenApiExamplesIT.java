package org.larder.contract;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The documented OpenAPI examples replayed against the running service (example-based contract check).
 */
class OpenApiExamplesIT {

  @Disabled("step 12 test list")
  @DisplayName("D1 createHelpRequest example -> 201 HelpRequestLink; provideHelp example on that request -> 201 HelpLink; getHelpRequestById -> 200 HelpRequest; each body valid against the spec -- B3-S01,B3-S02 US-CA-03 op createHelpRequest,provideHelp,getHelpRequestById")
  @Test
  void documentedExamplesAreAnsweredAsSpecified() {}
}
