package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.larder.cookingassistance.adapter.messaging.generated.AskedProvider;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProvidedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProvidedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProviderType;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCreatedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCreatedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpType;
import org.larder.cookingassistance.adapter.messaging.generated.PreparationStepExplanation;
import org.larder.cookingassistance.adapter.messaging.generated.PreparationStepExplanationAnswerType;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpAccepted;
import org.larder.cookingassistance.domain.helprequest.HelpProvided;
import org.larder.cookingassistance.domain.helprequest.HelpRejected;
import org.larder.cookingassistance.domain.helprequest.HelpRequestCancelled;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.domain.helprequest.HelpRequestTimedOut;
import org.larder.cookingassistance.domain.helprequest.HelpRequested;

/**
 * Turns domain events into outbox rows: the publish decision per event (an exhaustive switch over
 * the sealed event union) and the payload as the message model generated from the AsyncAPI spec.
 */
final class OutboxEventMapper {

  private OutboxEventMapper() {}

  static OutboxEvent toOutboxEvent(HelpRequestEvent event) {
    return switch (event) {
      case HelpRequested requested -> toOutboxEvent(requested);
      case HelpProvided provided -> toOutboxEvent(provided);
      // placeholder until step 7 of increment 1 maps it test-first
      case HelpRequestCancelled cancelled ->
          throw new UnsupportedOperationException("outbox mapping: step 7, increment 1");
      case HelpAccepted accepted ->
          throw new UnsupportedOperationException("outbox mapping: step 7, increment 1");
      case HelpRejected rejected ->
          throw new UnsupportedOperationException("outbox mapping: step 7, increment 1");
      case HelpRequestTimedOut timedOut ->
          throw new UnsupportedOperationException("outbox mapping: step 7, increment 1");
    };
  }

  // evt: Help provided -> AsyncAPI HelpProvided
  private static OutboxEvent toOutboxEvent(HelpProvided event) {
    return new OutboxEvent(
        "HelpProvided",
        json(
            new HelpProvidedPayload(
                event.helpId().value(),
                event.helpRequest().value(),
                event.helpRequester().value(),
                event.answerTitle(),
                HelpProviderType.fromValue(event.helpProviderType().name()),
                event.helpProvider().map(CookId::value).orElse(null),
                new PreparationStepExplanation(
                    PreparationStepExplanationAnswerType.PREPARATION_STEP_EXPLANATION,
                    event.answer().recipe().value(),
                    event.answer().howToStep().value(),
                    event.answer().description(),
                    event.answer().images().stream().map(URI::toString).toList(),
                    null),
                HelpProvidedStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  // evt: Help requested -> AsyncAPI HelpRequestCreated
  private static OutboxEvent toOutboxEvent(HelpRequested event) {
    return new OutboxEvent(
        "HelpRequestCreated",
        json(
            new HelpRequestCreatedPayload(
                event.helpRequestId().value(),
                event.requester().value(),
                event.title(),
                HelpType.fromValue(event.type().name()),
                event.description(),
                event.recipe().value(),
                event.howToStep().value(),
                null,
                null,
                null,
                null,
                null,
                null,
                event.askedProviders().stream()
                    .map(
                        asked ->
                            new AskedProvider(
                                HelpProviderType.fromValue(asked.providerType().name()),
                                null,
                                utc(asked.deadline()),
                                null))
                    .toList(),
                null,
                HelpRequestCreatedStatus.fromValue(event.status().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  private static OffsetDateTime utc(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static String json(Object payload) {
    try {
      return AsyncApiJson.MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write the event payload as JSON", e);
    }
  }
}
