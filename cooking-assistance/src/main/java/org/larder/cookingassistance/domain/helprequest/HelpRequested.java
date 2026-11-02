package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.List;

// slice: B3-S01, story: US-CA-03, evt: Help requested (AsyncAPI HelpRequestCreated)
/** A cook has requested help. */
public record HelpRequested(
    HelpRequestId helpRequestId,
    CookId requester,
    String title,
    HelpType type,
    String description,
    RecipeId recipe,
    HowToStepId howToStep,
    List<AskedProvider> askedProviders,
    HelpRequestStatus status,
    Instant occurredAt)
    implements HelpRequestEvent {}
