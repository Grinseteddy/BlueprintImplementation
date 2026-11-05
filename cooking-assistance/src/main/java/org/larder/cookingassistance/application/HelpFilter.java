package org.larder.cookingassistance.application;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpType;

/** The filters of listHelps; an empty one does not narrow the list. */
public record HelpFilter(Optional<CookId> helpProvider, Optional<HelpType> type) {}
