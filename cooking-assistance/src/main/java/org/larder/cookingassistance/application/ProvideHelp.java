package org.larder.cookingassistance.application;

import java.time.Clock;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
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
    admitCommunityAnswer(command);
    // ADR0010: another writer saved first — reload and retry once
    return RetryOnConflict.once(() -> provideHelpOnce(command));
  }

  /** ADR0013, deny by default: decided before the help request is loaded. */
  private static void admitCommunityAnswer(Command command) {
    // ADR0013: through the API only the provider type COMMUNITY answers; chef answers come in
    // increment 4
    if (command.providerType() != HelpProviderType.COMMUNITY) {
      throw new NotAuthorized("Provide Help as " + command.providerType(), Role.COMMUNITY);
    }
    // ADR0013: only Community members answer through the API
    command.actor().require(Role.COMMUNITY, "Provide Help");
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
      HelpProviderType providerType,
      HelpRequestId helpRequestId,
      String answerTitle,
      PreparationStepExplanation answer) {}
}
