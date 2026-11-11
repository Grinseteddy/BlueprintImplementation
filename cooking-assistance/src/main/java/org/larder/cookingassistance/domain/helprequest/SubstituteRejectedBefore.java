package org.larder.cookingassistance.domain.helprequest;

// INV-15: a substitute the Cook rejected is not proposed again
/** An answer proposing a substitute the Cook rejected earlier. */
public final class SubstituteRejectedBefore extends DomainRejection {

  public SubstituteRejectedBefore() {
    super("INV-15", "SUBSTITUTE_REJECTED_BEFORE", "This substitute was rejected before");
  }
}
