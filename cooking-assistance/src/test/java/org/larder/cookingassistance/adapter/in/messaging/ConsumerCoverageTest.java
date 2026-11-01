package org.larder.cookingassistance.adapter.in.messaging;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Coverage gate: every receive operation of the AsyncAPI spec has a registered consumer.
 */
class ConsumerCoverageTest {

  @Disabled("step 9 test list")
  @DisplayName("the receive operations of cooking-assistance.asyncapi.yaml equal the registered consumers (consumeGrandmaHelpProvided) -- B3-S02 US-CA-03 ADR0014")
  @Test
  void everyReceiveOperationHasAConsumer() {}
}
