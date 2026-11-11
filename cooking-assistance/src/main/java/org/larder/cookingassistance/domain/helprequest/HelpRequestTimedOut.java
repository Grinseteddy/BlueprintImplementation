package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.List;

// slice: B3-S06, story: US-CA-03/US-CA-02, evt: Help request timed out (AsyncAPI
// HelpRequestTimedOut)
/**
 * No asked provider answered in time; the request is cancelled and providers not yet asked are
 * suggested.
 */
public record HelpRequestTimedOut(
    HelpRequestId helpRequest,
    CookId helpRequester,
    List<HelpProviderType> timedOutProviders,
    List<HelpProviderType> suggestedProviders,
    HelpRequestStatus helpRequestStatus,
    Instant occurredAt)
    implements HelpRequestEvent {}
