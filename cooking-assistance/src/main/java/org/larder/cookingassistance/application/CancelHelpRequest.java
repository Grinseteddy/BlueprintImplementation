package org.larder.cookingassistance.application;

import java.time.Clock;
import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

// cmd: Cancel help request · agg: Help Request · slice: B3-S03, story: US-CA-03
/** Use case: the requester cancels a help request that is Open or Answered. */
public class CancelHelpRequest {

  private final HelpRequestRepository helpRequests;
  private final Clock clock;

  public CancelHelpRequest(HelpRequestRepository helpRequests, Clock clock) {
    this.helpRequests = helpRequests;
    this.clock = clock;
  }

  public void execute(Command command) {
    // ADR0010: another writer saved first — reload and retry once
    RetryOnConflict.once(() -> cancelOnce(command));
  }

  private void cancelOnce(Command command) {
    HelpRequest request =
        helpRequests
            .findById(command.helpRequestId())
            .orElseThrow(() -> new HelpRequestNotFound(command.helpRequestId()));
    // ADR0013: only the requester cancels
    command.actor().requireRequesterOf(request, "Cancel help request");
    request.cancel(command.note(), clock);
    helpRequests.save(request);
  }

  /** The cancellation in domain types; step 9 maps the HTTP request to it. */
  public record Command(Actor actor, HelpRequestId helpRequestId, Optional<String> note) {}
}
