package org.larder.cookingassistance.adapter.in;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Wiring through the real adapters and PostgreSQL: request or message in -> rows and outbox entries out.
 */
class InboundWiringIT {

  @Disabled("step 9 test list")
  @DisplayName("POST /help-requests with a signed token -> help_request row and outbox HelpRequestCreated -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void createHelpRequestStoresRowAndOutboxEntry() {}

  @Disabled("step 9 test list")
  @DisplayName("POST /help-requests/{id}/helps -> help row and outbox HelpProvided -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void provideHelpStoresHelpAndOutboxEntry() {}

  @Disabled("step 9 test list")
  @DisplayName("Grandma message -> help row, processed_message row and outbox HelpProvided -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void grandmaMessageStoresHelpProcessedMessageAndOutboxEntry() {}
}
