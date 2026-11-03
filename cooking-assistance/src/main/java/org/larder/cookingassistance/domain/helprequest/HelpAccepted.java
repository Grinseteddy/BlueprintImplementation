package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.Optional;

// slice: B3-S04, story: US-CA-03, evt: Help accepted (AsyncAPI HelpAccepted)
/** The requester accepted a Help; the request is closed. */
public record HelpAccepted(
    HelpRequestId helpRequest,
    CookId helpRequester,
    HelpId helpId,
    HelpProviderType helpProviderType,
    Optional<CookId> helpProvider,
    HelpRequestStatus helpRequestStatus,
    Instant occurredAt)
    implements HelpRequestEvent {}
