package org.larder.cookingassistance.domain.helprequest;

// INV-09: a request times out only after every asked provider's deadline (step 3 r5)
/** The clock asked to time out a request whose deadline has not passed yet. */
public final class DeadlineNotPassed extends DomainRejection {

  public DeadlineNotPassed() {
    super("INV-09", "INVALID_STATE", "Not every asked provider's deadline has passed");
  }
}
