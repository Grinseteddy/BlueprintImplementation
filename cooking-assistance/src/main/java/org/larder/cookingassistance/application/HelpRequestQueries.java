package org.larder.cookingassistance.application;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;

/** Query port of the read side (ADR0016): never loads or changes the aggregate. */
public interface HelpRequestQueries {

  Optional<HelpRequestView> byId(HelpRequestId id);
}
