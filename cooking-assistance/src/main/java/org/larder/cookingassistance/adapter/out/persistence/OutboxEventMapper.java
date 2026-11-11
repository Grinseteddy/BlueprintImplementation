package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.larder.cookingassistance.adapter.messaging.generated.AskedProvider;
import org.larder.cookingassistance.adapter.messaging.generated.HelpAcceptedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpAcceptedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProvidedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProvidedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpProviderType;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRejectedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRejectedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCancelledPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCancelledStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCreatedPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestCreatedStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestTimedOutPayload;
import org.larder.cookingassistance.adapter.messaging.generated.HelpRequestTimedOutStatus;
import org.larder.cookingassistance.adapter.messaging.generated.HelpType;
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
      case HelpRequestCancelled cancelled -> toOutboxEvent(cancelled);
      case HelpAccepted accepted -> toOutboxEvent(accepted);
      case HelpRejected rejected -> toOutboxEvent(rejected);
      case HelpRequestTimedOut timedOut -> toOutboxEvent(timedOut);
    };
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
                event.howToStep() == null ? null : event.howToStep().value(),
                event.ingredient() == null ? null : event.ingredient().value(),
                event.ingredientName(),
                event.substituteRequirements(),
                null,
                null,
                null,
                null,
                askedProviders(event.askedProviders()),
                null,
                HelpRequestCreatedStatus.fromValue(event.status().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
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
                providerType(event.helpProviderType()),
                event.helpProvider().map(CookId::value).orElse(null),
                AnswerMessageMapper.toMessage(event.answer()),
                HelpProvidedStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  // evt: Help request cancelled -> AsyncAPI HelpRequestCancelled
  private static OutboxEvent toOutboxEvent(HelpRequestCancelled event) {
    return new OutboxEvent(
        "HelpRequestCancelled",
        json(
            new HelpRequestCancelledPayload(
                event.helpRequest().value(),
                event.helpRequester().value(),
                event.note().orElse(null),
                askedProviders(event.askedProviders()),
                HelpRequestCancelledStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  // evt: Help accepted -> AsyncAPI HelpAccepted
  private static OutboxEvent toOutboxEvent(HelpAccepted event) {
    return new OutboxEvent(
        "HelpAccepted",
        json(
            new HelpAcceptedPayload(
                event.helpRequest().value(),
                event.helpRequester().value(),
                event.helpId().value(),
                providerType(event.helpProviderType()),
                event.helpProvider().map(CookId::value).orElse(null),
                HelpAcceptedStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  // evt: Help rejected -> AsyncAPI HelpRejected
  private static OutboxEvent toOutboxEvent(HelpRejected event) {
    return new OutboxEvent(
        "HelpRejected",
        json(
            new HelpRejectedPayload(
                event.helpRequest().value(),
                event.helpRequester().value(),
                event.helpId().value(),
                event.note(),
                askedProviders(event.askedProviders()),
                HelpRejectedStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  // evt: Help request timed out -> AsyncAPI HelpRequestTimedOut
  private static OutboxEvent toOutboxEvent(HelpRequestTimedOut event) {
    return new OutboxEvent(
        "HelpRequestTimedOut",
        json(
            new HelpRequestTimedOutPayload(
                event.helpRequest().value(),
                event.helpRequester().value(),
                event.timedOutProviders().stream().map(OutboxEventMapper::providerType).toList(),
                event.suggestedProviders().stream().map(OutboxEventMapper::providerType).toList(),
                HelpRequestTimedOutStatus.fromValue(event.helpRequestStatus().name()),
                utc(event.occurredAt()),
                null)),
        event.occurredAt());
  }

  private static List<AskedProvider> askedProviders(
      List<org.larder.cookingassistance.domain.helprequest.AskedProvider> asked) {
    return asked.stream()
        .map(
            provider ->
                new AskedProvider(
                    providerType(provider.providerType()), null, utc(provider.deadline()), null))
        .toList();
  }

  private static HelpProviderType providerType(
      org.larder.cookingassistance.domain.helprequest.HelpProviderType type) {
    return HelpProviderType.fromValue(type.name());
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
