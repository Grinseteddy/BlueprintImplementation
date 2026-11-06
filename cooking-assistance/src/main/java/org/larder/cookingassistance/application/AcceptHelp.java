package org.larder.cookingassistance.application;

import java.time.Clock;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

// cmd: Accept help · agg: Help Request · slice: B3-S04, story: US-CA-03
/** Use case: the requester accepts one Help of an answered request, which closes it. */
public class AcceptHelp {

  private final HelpRequestRepository helpRequests;
  private final Clock clock;

  public AcceptHelp(HelpRequestRepository helpRequests, Clock clock) {
    this.helpRequests = helpRequests;
    this.clock = clock;
  }

  public void execute(Command command) {
    // ADR0010: another writer saved first — reload and retry once
    RetryOnConflict.once(() -> acceptOnce(command));
  }

  private void acceptOnce(Command command) {
    HelpRequest request =
        helpRequests
            .findById(command.helpRequestId())
            .orElseThrow(() -> new HelpRequestNotFound(command.helpRequestId()));
    // ADR0013: only the requester accepts
    command.actor().requireRequesterOf(request, "Accept help");
    request.accept(command.helpId(), clock);
    helpRequests.save(request);
  }

  /** The acceptance in domain types; step 9 maps the HTTP request to it. */
  public record Command(Actor actor, HelpRequestId helpRequestId, HelpId helpId) {}
}
