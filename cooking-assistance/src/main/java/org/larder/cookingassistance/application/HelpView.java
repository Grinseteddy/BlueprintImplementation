package org.larder.cookingassistance.application;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;

/** A Help as the read side shows it, with the request it answers: plain values. */
public record HelpView(
    HelpId id,
    HelpRequestId helpRequest,
    CookId helpRequester,
    String answerTitle,
    HelpProvider provider,
    PreparationStepExplanation answer,
    boolean accepted,
    Optional<String> rejectionNote) {}
