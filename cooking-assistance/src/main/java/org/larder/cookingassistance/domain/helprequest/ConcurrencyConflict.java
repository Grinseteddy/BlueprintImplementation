package org.larder.cookingassistance.domain.helprequest;

/** The Help Request was changed by someone else since it was loaded (ADR0010). */
public final class ConcurrencyConflict extends RuntimeException {

  public ConcurrencyConflict(HelpRequestId id) {
    super("Help request " + id.value() + " was changed concurrently");
  }
}
