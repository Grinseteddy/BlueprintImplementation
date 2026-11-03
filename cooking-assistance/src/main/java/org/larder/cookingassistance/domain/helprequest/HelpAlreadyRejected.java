package org.larder.cookingassistance.domain.helprequest;

// INV-39: a rejected answer is never accepted or rejected again (step 3 r5)
/** The requester named a Help they had already rejected. */
public final class HelpAlreadyRejected extends DomainRejection {

  public HelpAlreadyRejected() {
    super("INV-39", "INVALID_STATE", "This help was rejected before");
  }
}
