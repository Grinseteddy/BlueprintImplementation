package org.larder.cookingassistance.application;

import java.time.Clock;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;

// cmd: Provide Help · agg: Help Request · slice: B3-S02, story: US-CA-03
/** Use case: a Community member answers a help request through the REST API. */
public class ProvideHelp {

  private final HelpRequestRepository helpRequests;
  private final IdGenerator ids;
  private final Clock clock;

  public ProvideHelp(HelpRequestRepository helpRequests, IdGenerator ids, Clock clock) {
    this.helpRequests = helpRequests;
    this.ids = ids;
    this.clock = clock;
  }

  public HelpId execute(Command command) {
    // ADR0013: only Community members answer through the API
    command.actor().require(Role.COMMUNITY, "Provide Help");
    try {
      return provideHelpOnce(command);
    } catch (ConcurrencyConflict conflict) {
      // ADR0010: another writer saved first — reload and retry once
      return provideHelpOnce(command);
    }
  }

  private HelpId provideHelpOnce(Command command) {
    HelpRequest request =
        helpRequests
            .findById(command.helpRequestId())
            .orElseThrow(() -> new HelpRequestNotFound(command.helpRequestId()));
    HelpId helpId = new HelpId(ids.next());
    request.provideHelp(
        helpId,
        HelpProvider.community(command.actor().id()),
        command.answerTitle(),
        command.answer(),
        clock);
    helpRequests.save(request);
    return helpId;
  }

  /** The answer in domain types; step 9 maps the HTTP body to it. */
  public record Command(
      Actor actor,
      HelpRequestId helpRequestId,
      String answerTitle,
      PreparationStepExplanation answer) {}
}
