package org.larder.cookingassistance.domain.helprequest;

// INV-05: only a Help given to this request can be accepted or rejected
/** The Help named is not part of this Help Request. */
public final class HelpNotInRequest extends DomainRejection {

  public HelpNotInRequest() {
    super("INV-05", "NOT_FOUND", "The help is not part of this help request");
  }
}
