package org.larder.cookingassistance.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.application.fakes.InMemoryProcessedMessages;
import org.larder.cookingassistance.application.fakes.RecordingUnitOfWork;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.fixtures.HelpRequests;

/**
 * Receive operation consumeGrandmaHelpProvided: the raw message as the transport hands it over ->
 * handler input in our language and the outcome for the transport (ADR0015).
 */
class GrandmaHelpProvidedConsumerTest {

  static final UUID M1 = UUID.fromString("8d9e0f1a-2b3c-4d5e-8f6a-7b8c9d0e1f77");
  static final Map<String, Object> HEADERS =
      Map.of(
          "correlationId", "0b8f7a52-4bfa-4d4e-9d6d-1f4b9c8f9a10",
          "messageId", M1.toString(),
          "source", "grandma-avatar");
  static final String GRANDMAS_ANSWER =
      """
      {
        "helpRequest": "%s",
        "answerTitle": "Butter in a thin stream",
        "answer": {
          "answerType": "PREPARATION_STEP_EXPLANATION",
          "recipe": "%s",
          "howToStep": "%s",
          "description": "Add the butter in a thin stream"
        }
      }
      """
          .formatted(
              HelpRequests.HELP_REQUEST_ID.value(),
              HelpRequests.HOLLANDAISE.value(),
              HelpRequests.STEP_4.value());

  final InMemoryHelpRequestRepository helpRequests = new InMemoryHelpRequestRepository();
  final InMemoryProcessedMessages processedMessages = new InMemoryProcessedMessages();
  final GrandmaHelpProvidedConsumer consumer =
      new GrandmaHelpProvidedConsumer(
          new ProvideGrandmaAvatarHelp(
              helpRequests,
              processedMessages,
              new RecordingUnitOfWork(),
              () -> HelpRequests.HELP_ID.value(),
              HelpRequests.FRIDAY_18_10));

  @BeforeEach
  void lenasRequestToGrandmaIsStored() {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  static byte[] bytes(String json) {
    return json.getBytes(StandardCharsets.UTF_8);
  }

  @DisplayName(
      "valid message -> the handler gets GrandmaAnswer(messageId, HelpRequestId, title, PSE); Grandma's help is stored -> ACK -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void validMessageProvidesGrandmasHelp() {
    Outcome outcome = consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER));

    assertThat(outcome).isEqualTo(Outcome.ACK);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(
            help -> {
              assertThat(help.providerType()).isEqualTo(HelpProviderType.GRANDMA_AVATAR);
              assertThat(help.answerTitle()).isEqualTo("Butter in a thin stream");
              assertThat(help.answer().howToStep()).isEqualTo(HelpRequests.STEP_4);
            });
    assertThat(processedMessages.recordFirstProcessing(M1)).as("M1 already recorded").isFalse();
  }

  @DisplayName(
      "valid message with unknown fields in the payload and in the answer -> they are ignored, Grandma's help is stored -> ACK -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided (tolerant reader)")
  @Test
  void unknownFieldsAreIgnored() {
    String withUnknownFields =
        GRANDMAS_ANSWER
            .replace("\"answerTitle\":", "\"mood\": \"cheerful\",\n  \"answerTitle\":")
            .replace("\"description\":", "\"difficulty\": \"easy\",\n    \"description\":");

    Outcome outcome = consumer.handle(HEADERS, bytes(withUnknownFields));

    assertThat(outcome).isEqualTo(Outcome.ACK);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(help -> assertThat(help.answerTitle()).isEqualTo("Butter in a thin stream"));
  }

  @DisplayName(
      "valid message whose source is not grandma-avatar -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 OWASP-API10 op consumeGrandmaHelpProvided")
  @Test
  void messageFromAnotherSourceIsDeadLettered() {
    Map<String, Object> fromElsewhere = new HashMap<>(HEADERS);
    fromElsewhere.put("source", "evil-system");

    expectDeadLetteredWithoutEffect(fromElsewhere, bytes(GRANDMAS_ANSWER));
  }

  @DisplayName(
      "Grandma's answer with a javascript: picture link -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 INV-38 OWASP-API10 op consumeGrandmaHelpProvided")
  @Test
  void nonHttpsPictureIsDeadLettered() {
    String withScriptLink =
        GRANDMAS_ANSWER.replace(
            "\"description\":", "\"images\": [\"javascript:alert(1)\"],\n    \"description\":");

    expectDeadLetteredWithoutEffect(HEADERS, bytes(withScriptLink));
  }

  @DisplayName(
      "Grandma's answer with the picture link \"https://\" (passes the schema, no URI) -> DEAD_LETTER at once, nothing changed -- B3-S02 US-CA-03 INV-38 OWASP-A10 op consumeGrandmaHelpProvided")
  @Test
  void unparsablePictureLinkIsDeadLettered() {
    String withUnparsableLink =
        GRANDMAS_ANSWER.replace(
            "\"description\":", "\"images\": [\"https://\"],\n    \"description\":");

    expectDeadLetteredWithoutEffect(HEADERS, bytes(withUnparsableLink));
  }

  @DisplayName(
      "answerTitle missing -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void missingAnswerTitleIsDeadLettered() {
    String withoutTitle =
        GRANDMAS_ANSWER.replace("\"answerTitle\": \"Butter in a thin stream\",", "");

    expectDeadLetteredWithoutEffect(HEADERS, bytes(withoutTitle));
  }

  private void expectDeadLetteredWithoutEffect(Map<String, Object> headers, byte[] body) {
    assertThat(consumer.handle(headers, body)).isEqualTo(Outcome.DEAD_LETTER);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
  }

  @DisplayName(
      "messageId header missing -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void missingMessageIdIsDeadLettered() {
    Map<String, Object> withoutMessageId = new HashMap<>(HEADERS);
    withoutMessageId.remove("messageId");

    expectDeadLetteredWithoutEffect(withoutMessageId, bytes(GRANDMAS_ANSWER));
  }

  @DisplayName(
      "messageId \"abc\" -> DEAD_LETTER, nothing changed (formats checked) -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void messageIdThatIsNoUuidIsDeadLettered() {
    Map<String, Object> withInvalidMessageId = new HashMap<>(HEADERS);
    withInvalidMessageId.put("messageId", "abc");

    expectDeadLetteredWithoutEffect(withInvalidMessageId, bytes(GRANDMAS_ANSWER));
  }

  @DisplayName(
      "body is not JSON -> DEAD_LETTER, nothing changed -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void bodyThatIsNoJsonIsDeadLettered() {
    expectDeadLetteredWithoutEffect(HEADERS, bytes("{not json"));
  }

  @DisplayName(
      "the same message twice -> one help; ACK both times -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void sameMessageTwiceHasOneEffect() {
    Outcome first = consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER));
    Outcome redelivery = consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER));

    assertThat(List.of(first, redelivery)).containsOnly(Outcome.ACK);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
  }

  @DisplayName(
      "unknown help request -> DEAD_LETTER -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void unknownHelpRequestIsDeadLettered() {
    String forUnknownRequest =
        GRANDMAS_ANSWER.replace(
            HelpRequests.HELP_REQUEST_ID.value().toString(),
            "00000000-0000-7000-8000-000000000009");

    assertThat(consumer.handle(HEADERS, bytes(forUnknownRequest))).isEqualTo(Outcome.DEAD_LETTER);
  }

  @DisplayName(
      "Grandma Avatar not asked (ProviderNotAsked) -> DEAD_LETTER -- B3-S02 US-CA-03 INV-01 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void domainRejectionIsDeadLettered() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    expectDeadLetteredWithoutEffect(HEADERS, bytes(GRANDMAS_ANSWER));
  }

  @DisplayName(
      "repository throws an unexpected error -> RETRY -- B3-S02 US-CA-03 ADR0015 op consumeGrandmaHelpProvided")
  @Test
  void unexpectedErrorIsRetried() {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    assertThat(consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER))).isEqualTo(Outcome.RETRY);
  }

  @DisplayName(
      "answer of kind Substitutes -> DEAD_LETTER (increment 2) -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void substitutesAnswerIsDeadLettered() {
    String substitutes =
        """
        {
          "helpRequest": "%s",
          "answerTitle": "Use yogurt",
          "answer": {
            "answerType": "INGREDIENT_SUBSTITUTE",
            "recipe": "%s",
            "substitute": [
              {
                "ingredient": "8956b2e5-8d0c-470e-ae9c-6071bd7c5b0d",
                "substituteIngredients": [{"name": "yogurt", "value": 100, "unit": "GRAM"}]
              }
            ]
          }
        }
        """
            .formatted(HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.HOLLANDAISE.value());

    expectDeadLetteredWithoutEffect(HEADERS, bytes(substitutes));
  }
}
