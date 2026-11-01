package org.larder.cookingassistance.adapter.in.messaging;

import java.util.Map;
import java.util.UUID;
import org.larder.cookingassistance.adapter.messaging.generated.GrandmaHelpProvidedPayload;
import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// op: consumeGrandmaHelpProvided (channel cooking-assistance.grandma-help-provided) · cmd: Provide
// Help
/** Consumes the Grandma Avatar's answers as the transport hands them over. */
@Component
public class GrandmaHelpProvidedConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(GrandmaHelpProvidedConsumer.class);

  private final AsyncApiMessageReader messages = new AsyncApiMessageReader();
  private final ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp;

  public GrandmaHelpProvidedConsumer(ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp) {
    this.provideGrandmaAvatarHelp = provideGrandmaAvatarHelp;
  }

  public Outcome handle(Map<String, Object> headers, byte[] body) {
    try {
      messages.validateHeaders(headers);
      GrandmaHelpProvidedPayload payload =
          messages.readPayload(
              body, "GrandmaHelpProvidedPayload", GrandmaHelpProvidedPayload.class);
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
}
