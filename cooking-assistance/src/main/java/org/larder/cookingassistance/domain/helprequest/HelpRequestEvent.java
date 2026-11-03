package org.larder.cookingassistance.domain.helprequest;

/** Domain events recorded by the Help Request aggregate. */
public sealed interface HelpRequestEvent
    permits HelpRequested,
        HelpProvided,
        HelpRequestCancelled,
        HelpAccepted,
        HelpRejected,
        HelpRequestTimedOut {}
