package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.Optional;

// slice: B3-S02, story: US-CA-03, evt: Help provided (AsyncAPI HelpProvided)
/** A help provider has answered a Help Request. */
public record HelpProvided(
    HelpId helpId,
    HelpRequestId helpRequest,
    CookId helpRequester,
    String answerTitle,
    HelpProviderType helpProviderType,
    Optional<CookId> helpProvider,
    PreparationStepExplanation answer,
    HelpRequestStatus helpRequestStatus,
    Instant occurredAt)
    implements HelpRequestEvent {}
