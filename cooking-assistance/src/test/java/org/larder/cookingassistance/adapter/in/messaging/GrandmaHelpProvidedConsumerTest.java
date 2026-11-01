package org.larder.cookingassistance.adapter.in.messaging;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Receive operation consumeGrandmaHelpProvided: the raw message as the transport hands it over -> handler
 * input in our language and the outcome for the transport (ADR0015).
 */
class GrandmaHelpProvidedConsumerTest {

  @Disabled("step 9 test list")
  @DisplayName("valid message -> the handler gets GrandmaAnswer(messageId, HelpRequestId, title, PSE); Grandma's help is stored -> ACK -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void validMessageProvidesGrandmasHelp() {}

  @Disabled("step 9 test list")
  @DisplayName("answerTitle missing -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void missingAnswerTitleIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("messageId header missing -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void missingMessageIdIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("messageId \"abc\" -> DEAD_LETTER, nothing changed (formats checked) -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void messageIdThatIsNoUuidIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("body is not JSON -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void bodyThatIsNoJsonIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("the same message twice -> one help; ACK both times -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void sameMessageTwiceHasOneEffect() {}

  @Disabled("step 9 test list")
  @DisplayName("unknown help request -> DEAD_LETTER -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void unknownHelpRequestIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("Grandma Avatar not asked (ProviderNotAsked) -> DEAD_LETTER -- B3-S02 US-CA-03 INV-01 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void domainRejectionIsDeadLettered() {}

  @Disabled("step 9 test list")
  @DisplayName("repository throws an unexpected error -> RETRY -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void unexpectedErrorIsRetried() {}

  @Disabled("step 9 test list")
  @DisplayName("answer of kind Substitutes -> DEAD_LETTER (increment 2) -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void substitutesAnswerIsDeadLettered() {}
}
