package org.larder.cookingassistance.adapter.out.messaging;

import java.util.UUID;
import org.larder.cookingassistance.fixtures.HelpRequests;

/** Payloads as step 7 writes them into the outbox, valid against the AsyncAPI spec. */
final class OutboxPayloads {

  private OutboxPayloads() {}

  static String helpRequestCreated(UUID helpRequestId) {
    return """
        {"helpRequestId":"%s","requester":"%s","title":"Sauce splits",\
        "type":"PREPARATION_STEP_EXPLANATION","description":"My hollandaise splits at step 4",\
        "recipe":"%s","howToStep":"%s",\
        "askedProviders":[{"providerType":"COMMUNITY","deadline":"2026-11-07T06:00:00Z"}],\
        "status":"OPEN","occurredAt":"2026-11-06T18:00:00Z"}"""
        .formatted(
            helpRequestId, HelpRequests.LENA.value(), HelpRequests.HOLLANDAISE.value(),
            HelpRequests.STEP_4.value());
  }

  static String helpProvided(UUID helpRequestId) {
    return """
        {"helpId":"%s","helpRequest":"%s","helpRequester":"%s","answerTitle":"Butter in a thin stream",\
        "helpProviderType":"GRANDMA_AVATAR","answer":{"answerType":"PREPARATION_STEP_EXPLANATION",\
        "recipe":"%s","howToStep":"%s","description":"Add the butter in a thin stream","images":[]},\
        "helpRequestStatus":"ANSWERED","occurredAt":"2026-11-06T18:10:00Z"}"""
        .formatted(
            HelpRequests.HELP_ID.value(), helpRequestId, HelpRequests.LENA.value(),
            HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());
  }
}
