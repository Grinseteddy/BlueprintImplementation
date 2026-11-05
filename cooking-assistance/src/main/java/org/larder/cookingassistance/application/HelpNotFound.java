package org.larder.cookingassistance.application;

import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;

/** No Help with this id was given to this help request; mapped to 404 in step 9. */
public final class HelpNotFound extends RuntimeException {

  public HelpNotFound(HelpRequestId request, HelpId help) {
    super("Help " + help.value() + " of help request " + request.value() + " not found");
  }
}
