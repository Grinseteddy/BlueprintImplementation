package org.larder.cookingassistance.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.application.fakes.InMemoryProcessedMessages;
import org.larder.cookingassistance.application.fakes.RecordingUnitOfWork;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

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

  @DisplayName(
      "L5 a message that can never succeed -> DEAD_LETTER and one WARN line with its message id and the reason, never the answer's text -- B3-S02 US-CA-03 OWASP-A09 QS-14 SEC-06 ADR0015")
  @Test
  void deadLetterIsLogged() {
    String withoutTitle =
        GRANDMAS_ANSWER.replace("\"answerTitle\": \"Butter in a thin stream\",", "");

    List<ILoggingEvent> lines =
        consumerLogLines(() -> consumer.handle(HEADERS, bytes(withoutTitle)));

    assertThat(lines)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getLevel()).isEqualTo(Level.WARN);
              assertThat(line.getFormattedMessage())
                  .contains(HEADERS.get("messageId").toString(), "dead-letter")
                  .doesNotContain("Add the butter in a thin stream");
            });
  }

  @DisplayName(
      "L5b a message id with a line break -> DEAD_LETTER and one WARN line that has no line break: the sender cannot forge a log line -- B3-S02 US-CA-03 OWASP-A09 SEC-06")
  @Test
  void aMessageIdCannotForgeALogLine() {
    Map<String, Object> forged = new HashMap<>(HEADERS);
    forged.put("messageId", "x\n2026-11-08 ERROR forged line");

    List<ILoggingEvent> lines =
        consumerLogLines(
            () ->
                assertThat(consumer.handle(forged, bytes(GRANDMAS_ANSWER)))
                    .isEqualTo(Outcome.DEAD_LETTER));

    assertThat(lines)
        .singleElement()
        .satisfies(line -> assertThat(line.getFormattedMessage()).doesNotContain("\n"));
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

  @DisplayName(
      "Grandma's answer to a request that is cancelled, closed or timed out -> ACK (ignored), nothing changed, one INFO log line with the message id and the request's state -- B3-S03,B3-S04,B3-S06 US-CA-03 op consumeGrandmaHelpProvided (step 9 r2 from step 13, Q-A2)")
  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"cancelled", "closed", "timed out"})
  @ExtendWith(OutputCaptureExtension.class)
  void lateAnswerIsIgnored(String state, CapturedOutput output) {
    HelpRequest lenas = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    switch (state) {
      case "cancelled" -> lenas.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);
      case "closed" -> {
        HelpRequests.grandmaExplainsStep4(lenas);
        lenas.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);
      }
      default -> lenas.timeOut(Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC));
    }
    helpRequests.save(lenas);
    HelpRequest before = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();

    Outcome outcome = consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER));

    HelpRequest after = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(outcome).isEqualTo(Outcome.ACK);
    assertThat(after.status()).isEqualTo(before.status());
    assertThat(after.helps()).hasSameSizeAs(before.helps());
    assertThat(output.getOut())
        .containsOnlyOnce(M1.toString())
        .contains("INFO")
        .contains(before.status().name());
  }

  @DisplayName(
      "Grandma's answer after her deadline, the request still open -> ACK (ignored), no Help stored, one INFO log line with the message id and the reason -- B3-S02 INV-09 US-CA-03 op consumeGrandmaHelpProvided (step 9 r3 from step 13, Q-A2)")
  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void answerAfterItsDeadlineIsIgnored(CapturedOutput output) {
    GrandmaHelpProvidedConsumer oneSecondAfterTheDeadline =
        new GrandmaHelpProvidedConsumer(
            new ProvideGrandmaAvatarHelp(
                helpRequests,
                processedMessages,
                new RecordingUnitOfWork(),
                () -> HelpRequests.HELP_ID.value(),
                Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC)));

    Outcome outcome = oneSecondAfterTheDeadline.handle(HEADERS, bytes(GRANDMAS_ANSWER));

    assertThat(outcome).isEqualTo(Outcome.ACK);
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
    assertThat(output.getOut())
        .containsOnlyOnce(M1.toString())
        .contains("INFO")
        .contains("deadline");
  }

  /** The consumer's log lines while {@code handling} runs. */
  private static List<ILoggingEvent> consumerLogLines(Runnable handling) {
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    Logger consumerLog = (Logger) LoggerFactory.getLogger(GrandmaHelpProvidedConsumer.class);
    consumerLog.addAppender(lines);
    try {
      handling.run();
    } finally {
      consumerLog.detachAppender(lines);
    }
    return lines.list;
  }

  @DisplayName(
      "M3a Grandma's answer to Lena's cancelled request -> the INFO line 'ignored' has helpRequestId = Lena's request in its log context; afterwards it is cleared -- B3-S03 US-CA-03 ADR0020 QS-14 op consumeGrandmaHelpProvided")
  @Test
  void ignoredAnswerLogLineCarriesTheHelpRequestId() {
    HelpRequest lenas = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    lenas.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);
    helpRequests.save(lenas);

    List<ILoggingEvent> lines =
        consumerLogLines(() -> consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER)));

    assertThat(lines)
        .filteredOn(line -> line.getFormattedMessage().contains("ignored"))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line.getMDCPropertyMap())
                    .containsEntry(
                        "helpRequestId", HelpRequests.HELP_REQUEST_ID.value().toString()));
    assertThat(MDC.get("helpRequestId")).isNull();
  }

  @DisplayName(
      "M3b repository throws an unexpected error -> the WARN line 'retrying' has helpRequestId = Lena's request in its log context -- B3-S02 US-CA-03 ADR0020 QS-14 op consumeGrandmaHelpProvided")
  @Test
  void retriedAnswerLogLineCarriesTheHelpRequestId() {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    List<ILoggingEvent> lines =
        consumerLogLines(() -> consumer.handle(HEADERS, bytes(GRANDMAS_ANSWER)));

    assertThat(lines)
        .filteredOn(line -> line.getFormattedMessage().contains("retrying"))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line.getMDCPropertyMap())
                    .containsEntry(
                        "helpRequestId", HelpRequests.HELP_REQUEST_ID.value().toString()));
  }
}
