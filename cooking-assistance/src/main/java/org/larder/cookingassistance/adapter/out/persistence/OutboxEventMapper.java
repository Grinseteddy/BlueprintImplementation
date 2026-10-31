package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpProvided;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.domain.helprequest.HelpRequested;

/** Turns domain events into outbox rows under their AsyncAPI message names and field names. */
final class OutboxEventMapper {

  private OutboxEventMapper() {}

  static OutboxEvent toOutboxEvent(HelpRequestEvent event) {
    return switch (event) {
      case HelpRequested requested -> toOutboxEvent(requested);
      case HelpProvided provided -> toOutboxEvent(provided);
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
                event.helpProviderType().name(),
                event.helpProvider().map(CookId::value).orElse(null),
                AnswerJsonMapper.toPayload(event.answer()),
                event.helpRequestStatus().name(),
                event.occurredAt())),
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
                event.type().name(),
                event.description(),
                event.recipe().value(),
                event.howToStep().value(),
                event.askedProviders().stream()
                    .map(
                        asked ->
                            new AskedProviderPayload(
                                asked.providerType().name(), asked.deadline()))
                    .toList(),
                event.status().name(),
                event.occurredAt())),
        event.occurredAt());
  }

  private static String json(Object payload) {
    try {
      return AsyncApiJson.MAPPER.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Cannot write the event payload as JSON", e);
    }
  }

  /** AsyncAPI HelpRequestCreatedPayload. */
  record HelpRequestCreatedPayload(
      UUID helpRequestId,
      UUID requester,
      String title,
      String type,
      String description,
      UUID recipe,
      UUID howToStep,
      List<AskedProviderPayload> askedProviders,
      String status,
      Instant occurredAt) {}

  /** AsyncAPI HelpProvidedPayload; helpProvider absent for the Grandma Avatar. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  record HelpProvidedPayload(
      UUID helpId,
      UUID helpRequest,
      UUID helpRequester,
      String answerTitle,
      String helpProviderType,
      UUID helpProvider,
      AnswerJsonMapper.PreparationStepExplanationJson answer,
      String helpRequestStatus,
      Instant occurredAt) {}

  /** AsyncAPI AskedProvider. */
  record AskedProviderPayload(String providerType, Instant deadline) {}
}
