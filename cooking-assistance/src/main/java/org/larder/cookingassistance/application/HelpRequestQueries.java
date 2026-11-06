package org.larder.cookingassistance.application;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;

/** Query port of the read side (ADR0016): never loads or changes the aggregate. */
public interface HelpRequestQueries {

  Optional<HelpRequestView> byId(HelpRequestId id);

  /** The Helps given to a help request, oldest first (ADR0016 L6); empty if no such request. */
  Optional<Page<HelpView>> helpsOf(HelpRequestId id, int limit, Optional<HelpId> after);

  /** One Help of a help request; empty if the request has no Help with this id. */
  Optional<HelpView> helpOf(HelpRequestId request, HelpId help);

  /** The Helps the filter lets through, across help requests, oldest first (ADR0016 L6). */
  Page<HelpView> helps(HelpFilter filter, int limit, Optional<HelpId> after);

  /** The help requests the filter lets through, oldest first (ADR0016 L6). */
  Page<HelpRequestView> list(HelpRequestFilter filter, int limit, Optional<HelpRequestId> after);
}
