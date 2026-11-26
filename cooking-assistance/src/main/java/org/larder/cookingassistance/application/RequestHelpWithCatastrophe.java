package org.larder.cookingassistance.application;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

// cmd: Request help Catastrophe · agg: Help Request · slice: B4-S01, story: US-CA-04
/** Use case: a cook requests help with a catastrophe in the kitchen. */
public class RequestHelpWithCatastrophe {

  private final HelpRequestRepository helpRequests;
  private final IdGenerator ids;
  private final Clock clock;

  public RequestHelpWithCatastrophe(
      HelpRequestRepository helpRequests, IdGenerator ids, Clock clock) {
    this.helpRequests = helpRequests;
    this.ids = ids;
    this.clock = clock;
  }

  public HelpRequestId execute(Command command) {
    // ADR0013: only cooks request help
    command.actor().require(Role.COOK, "Request help Catastrophe");
    HelpRequest request =
        HelpRequest.requestHelpWithCatastrophe(
            new HelpRequestId(ids.next()),
            command.actor().id(),
            command.title(),
            command.description(),
            command.recipe(),
            command.pictures(),
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
      Optional<RecipeId> recipe,
      List<URI> pictures,
      Set<HelpProviderType> preferredProviders) {}
}
