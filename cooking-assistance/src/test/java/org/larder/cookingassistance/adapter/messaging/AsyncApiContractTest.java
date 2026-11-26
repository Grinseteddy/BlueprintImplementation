package org.larder.cookingassistance.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
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

  @DisplayName(
      "Grandma's mitigation with an image link of 2049 characters -> the payload schema reports a violation (maxLength 2048) -- B4-S02 US-CA-04 INV-38 OWASP-API4 op consumeGrandmaHelpProvided")
  @Test
  void pictureLinksAreAtMost2048Characters() throws Exception {
    String prefix = "https://img.larder.example/";
    var payload =
        new ObjectMapper()
            .readTree(
                """
                {
                  "helpRequest": "4d1f7c3a-8b2e-4f6a-9c0d-1e2f3a4b5c6d",
                  "answerTitle": "Save the chicken",
                  "answer": {
                    "answerType": "STEPS_TO_MITIGATE_CATASTROPHE",
                    "explanation": "cut off the burnt part, make a fresh sauce",
                    "images": ["%s"]
                  }
                }
                """
                    .formatted(prefix + "a".repeat(2049 - prefix.length())));

    assertThat(new AsyncApiContract().violations("GrandmaHelpProvidedPayload", payload))
        .isNotEmpty();
  }

  @DisplayName(
      "every message example of the spec -> its payload and headers match their schemas (the lint checks AsyncAPI 2 examples only) -- B2-S01,B2-S02,B4-S01,B4-S02 US-CA-02,US-CA-03,US-CA-04 (step 4 r22, r26)")
  @Test
  void everyMessageExampleMatchesItsSchemas() throws Exception {
    AsyncApiContract contract = new AsyncApiContract();
    JsonNode messages;
    try (InputStream in =
        getClass().getClassLoader().getResourceAsStream("api/cooking-assistance.asyncapi.yaml")) {
      messages =
          new ObjectMapper(new YAMLFactory()).readTree(in).path("components").path("messages");
    }
    List<String> violations = new ArrayList<>();
    int examples = 0;
    for (JsonNode message : messages) {
      for (JsonNode example : message.path("examples")) {
        examples++;
        String where = message.path("name").asText() + "/" + example.path("name").asText();
        violations.addAll(
            violationsOf(contract, message.path("payload"), example.path("payload"), where));
        if (message.has("headers")) {
          violations.addAll(
              violationsOf(contract, message.path("headers"), example.path("headers"), where));
        }
      }
    }

    assertThat(examples).as("message examples read").isPositive();
    assertThat(violations).isEmpty();
  }

  private static List<String> violationsOf(
      AsyncApiContract contract, JsonNode reference, JsonNode value, String where) {
    String ref = reference.path("$ref").asText();
    return contract.violations(ref.substring(ref.lastIndexOf('/') + 1), value).stream()
        .map(violation -> where + ": " + violation)
        .toList();
  }
}
