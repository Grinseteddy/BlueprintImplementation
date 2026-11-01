package org.larder.cookingassistance.application;

import java.util.List;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** A help request as the read side shows it: plain values, no behavior. */
public record HelpRequestView(
    HelpRequestId id,
    CookId requester,
    String title,
    HelpType type,
    String description,
    RecipeId recipe,
    HowToStepId howToStep,
    List<AskedProvider> askedProviders,
    HelpRequestStatus status) {}
