package org.larder.cookingassistance.application;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;

/** The filters of listHelpRequests; an empty one does not narrow the list. */
public record HelpRequestFilter(
    Optional<CookId> requester, Optional<HelpRequestStatus> status, Optional<HelpType> type) {}
