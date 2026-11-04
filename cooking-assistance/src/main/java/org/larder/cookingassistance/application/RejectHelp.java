package org.larder.cookingassistance.application;

import java.time.Clock;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

// cmd: Reject help · agg: Help Request · slice: B3-S05, story: US-CA-03
/** Use case: the requester rejects a Help with a note; the request is asked again. */
public class RejectHelp {

  private final HelpRequestRepository helpRequests;
  private final Clock clock;

  public RejectHelp(HelpRequestRepository helpRequests, Clock clock) {
    this.helpRequests = helpRequests;
    this.clock = clock;
  }

  public void execute(Command command) {
    // ADR0010: another writer saved first — reload and retry once
    RetryOnConflict.once(() -> rejectOnce(command));
  }

  private void rejectOnce(Command command) {
    HelpRequest request =
        helpRequests
            .findById(command.helpRequestId())
            .orElseThrow(() -> new HelpRequestNotFound(command.helpRequestId()));
    // ADR0013: only the requester rejects
    command.actor().requireRequesterOf(request, "Reject help");
    request.reject(command.helpId(), command.note(), clock);
    helpRequests.save(request);
  }

  /** The rejection in domain types; step 9 maps the HTTP request to it. */
  public record Command(Actor actor, HelpRequestId helpRequestId, HelpId helpId, String note) {}
}
