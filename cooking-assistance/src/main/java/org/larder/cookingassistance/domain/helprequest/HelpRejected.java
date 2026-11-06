package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.List;

// slice: B3-S05, story: US-CA-03, evt: Help rejected (AsyncAPI HelpRejected)
/** The requester rejected a Help with a note; the request is sent again with new deadlines. */
public record HelpRejected(
    HelpRequestId helpRequest,
    CookId helpRequester,
    HelpId helpId,
    String note,
    List<AskedProvider> askedProviders,
    HelpRequestStatus helpRequestStatus,
    Instant occurredAt)
    implements HelpRequestEvent {}
