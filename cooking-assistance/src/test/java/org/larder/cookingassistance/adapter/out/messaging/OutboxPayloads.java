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
            helpRequestId,
            HelpRequests.LENA.value(),
            HelpRequests.HOLLANDAISE.value(),
            HelpRequests.STEP_4.value());
  }

  static String helpProvided(UUID helpRequestId) {
    return """
        {"helpId":"%s","helpRequest":"%s","helpRequester":"%s","answerTitle":"Butter in a thin stream",\
        "helpProviderType":"GRANDMA_AVATAR","answer":{"answerType":"PREPARATION_STEP_EXPLANATION",\
        "recipe":"%s","howToStep":"%s","description":"Add the butter in a thin stream","images":[]},\
        "helpRequestStatus":"ANSWERED","occurredAt":"2026-11-06T18:10:00Z"}"""
        .formatted(
            HelpRequests.HELP_ID.value(),
            helpRequestId,
            HelpRequests.LENA.value(),
            HelpRequests.HOLLANDAISE.value(),
            HelpRequests.STEP_4.value());
  }

  static String helpRequestCancelled(UUID helpRequestId) {
    return """
        {"helpRequest":"%s","helpRequester":"%s","note":"an ice cube saved it",\
        "askedProviders":[{"providerType":"GRANDMA_AVATAR","deadline":"2026-11-07T06:00:00Z"}],\
        "helpRequestStatus":"CANCELLED","occurredAt":"2026-11-06T18:10:00Z"}"""
        .formatted(helpRequestId, HelpRequests.LENA.value());
  }

  static String helpAccepted(UUID helpRequestId) {
    return """
        {"helpRequest":"%s","helpRequester":"%s","helpId":"%s","helpProviderType":"GRANDMA_AVATAR",\
        "helpRequestStatus":"CLOSED","occurredAt":"2026-11-06T18:20:00Z"}"""
        .formatted(helpRequestId, HelpRequests.LENA.value(), HelpRequests.HELP_ID.value());
  }

  static String helpRejected(UUID helpRequestId) {
    return """
        {"helpRequest":"%s","helpRequester":"%s","helpId":"%s","note":"warm water didn't help",\
        "askedProviders":[{"providerType":"GRANDMA_AVATAR","deadline":"2026-11-07T08:00:00Z"}],\
        "helpRequestStatus":"OPEN","occurredAt":"2026-11-06T20:00:00Z"}"""
        .formatted(helpRequestId, HelpRequests.LENA.value(), HelpRequests.HELP_ID.value());
  }

  static String helpRequestTimedOut(UUID helpRequestId) {
    return """
        {"helpRequest":"%s","helpRequester":"%s","timedOutProviders":["COMMUNITY"],\
        "suggestedProviders":[],"helpRequestStatus":"CANCELLED","occurredAt":"2026-11-07T06:00:10Z"}"""
        .formatted(helpRequestId, HelpRequests.LENA.value());
  }

  static String helpRequestCreatedForAnIngredient(UUID helpRequestId) {
    return """
        {"helpRequestId":"%s","requester":"%s","title":"No creme fraiche",\
        "type":"INGREDIENT_SUBSTITUTE","description":"The shop has none",\
        "recipe":"%s","ingredient":"%s","substituteRequirements":"200 g, not available",\
        "askedProviders":[{"providerType":"COMMUNITY","deadline":"2026-11-10T17:00:00Z"}],\
        "status":"OPEN","occurredAt":"2026-11-09T17:00:00Z"}"""
        .formatted(
            helpRequestId,
            HelpRequests.LENA.value(),
            HelpRequests.TOMATO_SOUP.value(),
            HelpRequests.CREME_FRAICHE.value());
  }

  static String helpProvidedWithSubstitutes(UUID helpRequestId) {
    return """
        {"helpId":"%s","helpRequest":"%s","helpRequester":"%s","answerTitle":"Yogurt",\
        "helpProviderType":"GRANDMA_AVATAR","answer":{"answerType":"INGREDIENT_SUBSTITUTE",\
        "recipe":"%s","substitute":[{"ingredient":"%s",\
        "substituteIngredients":[{"name":"yogurt","value":200.0,"unit":"GRAM"}]}]},\
        "helpRequestStatus":"ANSWERED","occurredAt":"2026-11-09T18:00:00Z"}"""
        .formatted(
            HelpRequests.HELP_ID.value(),
            helpRequestId,
            HelpRequests.LENA.value(),
            HelpRequests.TOMATO_SOUP.value(),
            HelpRequests.CREME_FRAICHE.value());
  }
}
