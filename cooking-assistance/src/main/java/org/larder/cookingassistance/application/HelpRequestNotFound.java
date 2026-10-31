package org.larder.cookingassistance.application;

import org.larder.cookingassistance.domain.helprequest.HelpRequestId;

/** No help request with this id exists; mapped to 404 in step 9. */
public final class HelpRequestNotFound extends RuntimeException {

  public HelpRequestNotFound(HelpRequestId id) {
    super("Help request " + id.value() + " not found");
  }
}
