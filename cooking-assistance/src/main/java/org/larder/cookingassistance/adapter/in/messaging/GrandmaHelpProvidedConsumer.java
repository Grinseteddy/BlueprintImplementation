package org.larder.cookingassistance.adapter.in.messaging;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.larder.cookingassistance.adapter.messaging.generated.GrandmaHelpProvidedPayload;
import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.domain.helprequest.AnswerTooLate;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

// op: consumeGrandmaHelpProvided (channel cooking-assistance.grandma-help-provided) · cmd: Provide
// Help
/** Consumes the Grandma Avatar's answers as the transport hands them over. */
@Component
public class GrandmaHelpProvidedConsumer {

  private static final Logger LOG = LoggerFactory.getLogger(GrandmaHelpProvidedConsumer.class);
  private static final String HELP_REQUEST_ID = "helpRequestId";
  private static final Pattern UUID_FORM =
      Pattern.compile("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}");

  private final AsyncApiMessageReader messages = new AsyncApiMessageReader();
  private final ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp;

  public GrandmaHelpProvidedConsumer(ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp) {
    this.provideGrandmaAvatarHelp = provideGrandmaAvatarHelp;
  }

  public Outcome handle(Map<String, Object> headers, byte[] body) {
    try {
      messages.validateHeaders(headers, "GrandmaMessageHeader");
      GrandmaHelpProvidedPayload payload =
          messages.readPayload(
              body, "GrandmaHelpProvidedPayload", GrandmaHelpProvidedPayload.class);
      // ADR0020, QS-14: the answer's help request is on the consumer's log lines
      MDC.put(HELP_REQUEST_ID, payload.helpRequest().toString());
      provideGrandmaAvatarHelp.handle(
          GrandmaAnswerMapper.toGrandmaAnswer(
              UUID.fromString(headers.get("messageId").toString()), payload));
      return Outcome.ACK;
    } catch (InvalidHelpRequestState | AnswerTooLate late) {
      // Q-A2: an answer after a cancel, an acceptance, a time-out or its deadline is ignored,
      // never dead-lettered
      LOG.info("Grandma Avatar answer {} ignored: {}", headers.get("messageId"), late.getMessage());
      return Outcome.ACK;
    } catch (InvalidMessage
        | HelpRequestNotFound
        | DomainRejection
        | AnswerKindNotBuilt neverSucceeds) {
      // ADR0015: a message that can never succeed goes to the dead-letter queue
      // SEC-06: the rejection's type only; its text can repeat what the sender wrote
      LOG.warn(
          "Grandma Avatar answer {} sent to the dead-letter queue: {}",
          loggableId(headers.get("messageId")),
          neverSucceeds.getClass().getSimpleName());
      return Outcome.DEAD_LETTER;
    } catch (RuntimeException mayPass) {
      // ADR0015: delivered again, dead-lettered after 5 deliveries
      LOG.warn("Grandma Avatar answer not processed, retrying", mayPass);
      return Outcome.RETRY;
    } finally {
      MDC.remove(HELP_REQUEST_ID);
    }
  }

  /** The message id is read before its header is valid: only a UUID may reach the log. */
  private static String loggableId(Object messageId) {
    String id = String.valueOf(messageId);
    return UUID_FORM.matcher(id).matches() ? id : "(invalid message id)";
  }
}
