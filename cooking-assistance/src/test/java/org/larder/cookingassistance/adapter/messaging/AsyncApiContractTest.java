package org.larder.cookingassistance.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The approved AsyncAPI spec as the adapters check messages against it. */
class AsyncApiContractTest {

  @DisplayName(
      "Grandma's answer with a javascript: picture link -> the payload schema reports a violation (https only) -- B3-S02 US-CA-03 INV-38 OWASP-API10 op consumeGrandmaHelpProvided")
  @Test
  void pictureLinksMustBeHttps() throws Exception {
    var payload =
        new ObjectMapper()
            .readTree(
                """
                {
                  "helpRequest": "23a8eeed-35f6-460b-892e-7bb458a8fded",
                  "answerTitle": "Butter in a thin stream",
                  "answer": {
                    "answerType": "PREPARATION_STEP_EXPLANATION",
                    "recipe": "7cf09822-77a1-46bb-812f-b7852bca0913",
                    "howToStep": "65610dee-fb83-4341-a730-26a4a99a621a",
                    "description": "Add the butter in a thin stream",
                    "images": ["javascript:alert(1)"]
                  }
                }
                """);

    assertThat(new AsyncApiContract().violations("GrandmaHelpProvidedPayload", payload))
        .isNotEmpty();
  }
}
