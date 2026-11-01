package org.larder.cookingassistance.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.larder.cookingassistance.adapter.messaging.AsyncApiContract;
import org.larder.cookingassistance.adapter.messaging.generated.GrandmaHelpProvidedPayload;
import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// op: consumeGrandmaHelpProvided (channel cooking-assistance.grandma-help-provided) · cmd: Provide Help
/** Consumes the Grandma Avatar's answers as the transport hands them over. */
@Component
public class GrandmaHelpProvidedConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(GrandmaHelpProvidedConsumer.class);

  private final ObjectMapper json = new ObjectMapper();
  private final AsyncApiContract contract = new AsyncApiContract();
  private final ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp;

  public GrandmaHelpProvidedConsumer(ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp) {
    this.provideGrandmaAvatarHelp = provideGrandmaAvatarHelp;
  }

  public Outcome handle(Map<String, Object> headers, byte[] body) {
    try {
      validate("MessageHeader", json.valueToTree(headers));
      GrandmaHelpProvidedPayload payload = read(body);
      provideGrandmaAvatarHelp.handle(
          GrandmaAnswerMapper.toGrandmaAnswer(
              UUID.fromString(headers.get("messageId").toString()), payload));
      return Outcome.ACK;
    } catch (InvalidMessage
        | HelpRequestNotFound
        | DomainRejection
        | AnswerKindNotBuilt neverSucceeds) {
      // ADR0015: a message that can never succeed goes to the dead-letter queue
      return Outcome.DEAD_LETTER;
    } catch (RuntimeException mayPass) {
      // ADR0015: delivered again, dead-lettered after 5 deliveries
      LOG.warn("Grandma Avatar answer not processed, retrying", mayPass);
      return Outcome.RETRY;
    }
  }

  private void validate(String schemaName, JsonNode node) {
    List<String> violations = contract.violations(schemaName, node);
    if (!violations.isEmpty()) {
      throw new InvalidMessage(violations);
    }
  }

  /** The payload, validated against the spec before it is mapped. */
  private GrandmaHelpProvidedPayload read(byte[] body) {
    JsonNode payload = parse(body);
    validate("GrandmaHelpProvidedPayload", payload);
    try {
      return json.treeToValue(payload, GrandmaHelpProvidedPayload.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private JsonNode parse(byte[] body) {
    try {
      return json.readTree(body);
    } catch (IOException notJson) {
      throw new InvalidMessage(List.of("the payload is not valid JSON"));
    }
  }
}
