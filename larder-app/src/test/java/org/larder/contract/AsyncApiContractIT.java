package org.larder.contract;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Receive operations checked from their AsyncAPI examples against the running service.
 */
class AsyncApiContractIT {

  @Disabled("step 12 test list")
  @DisplayName("D2 the GrandmaHelpProvided example is acked (nothing pending, nothing dead-lettered); without its first required field it is dead-lettered -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void receiveExampleIsAcceptedAndBrokenOneDeadLettered() {}
}
