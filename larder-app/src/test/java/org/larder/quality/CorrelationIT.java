package org.larder.quality;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QS-14 short check: one help journey correlates HTTP and messages (ADR0020).
 */
class CorrelationIT {

  @Disabled("step 12 test list")
  @DisplayName("E2 POST /help-requests -> the returned helpRequestId is the correlationId header of the published HelpRequestCreated -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void httpRequestAndPublishedMessageShareTheCorrelation() {}
}
