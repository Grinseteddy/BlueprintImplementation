package org.larder.cookingassistance.application;

import java.time.Clock;
import java.util.Set;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

// cmd: Request help Preparation step · agg: Help Request · slice: B3-S01, story: US-CA-03
/** Use case: a cook requests help with one preparation step. */
public class RequestHelpWithPreparationStep {

  private final HelpRequestRepository helpRequests;
  private final IdGenerator ids;
  private final Clock clock;

  public RequestHelpWithPreparationStep(
      HelpRequestRepository helpRequests, IdGenerator ids, Clock clock) {
    this.helpRequests = helpRequests;
    this.ids = ids;
    this.clock = clock;
  }

  public HelpRequestId execute(Command command) {
    // ADR0013: only cooks request help
    command.actor().require(Role.COOK, "Request help Preparation step");
    HelpRequest request =
        HelpRequest.requestHelpWithPreparationStep(
            new HelpRequestId(ids.next()),
            command.actor().id(),
            command.title(),
            command.description(),
            command.recipe(),
            command.howToStep(),
            command.preferredProviders(),
            clock);
    helpRequests.save(request);
    return request.id();
  }

  /** The request in domain types; step 9 maps the HTTP body to it. */
  public record Command(
      Actor actor,
      String title,
      String description,
      RecipeId recipe,
      HowToStepId howToStep,
      Set<HelpProviderType> preferredProviders) {}
}
