package org.larder.cookingassistance.domain.helprequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;

// slice: B3-S01/B2-S01/B4-S01, story: US-CA-03/US-CA-02/US-CA-04, evt: Help requested
// (AsyncAPI HelpRequestCreated)
/** A cook has requested help. */
public record HelpRequested(
    HelpRequestId helpRequestId,
    CookId requester,
    String title,
    HelpType type,
    String description,
    RecipeId recipe,
    HowToStepId howToStep,
    IngredientId ingredient,
    String ingredientName,
    String substituteRequirements,
    List<URI> pictures,
    List<AskedProvider> askedProviders,
    HelpRequestStatus status,
    Instant occurredAt)
    implements HelpRequestEvent {}
