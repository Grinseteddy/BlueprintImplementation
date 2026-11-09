package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

// slice: B3-S03, story: US-CA-03/US-CA-02, evt: Help request cancelled (AsyncAPI
// HelpRequestCancelled)
/** The requester cancelled a Help Request; the providers see the note. */
public record HelpRequestCancelled(
    HelpRequestId helpRequest,
    CookId helpRequester,
    Optional<String> note,
    List<AskedProvider> askedProviders,
    HelpRequestStatus helpRequestStatus,
    Instant occurredAt)
    implements HelpRequestEvent {}
