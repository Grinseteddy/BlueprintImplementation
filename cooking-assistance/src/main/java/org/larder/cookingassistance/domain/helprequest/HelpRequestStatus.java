package org.larder.cookingassistance.domain.helprequest;

/** Lifecycle status of a Help Request. */
public enum HelpRequestStatus {
  OPEN,
  ANSWERED,
  CLOSED,
  CANCELLED;

  /** CLOSED and CANCELLED are final: they accept no further command (design brief §3). */
  public boolean isFinal() {
    return this == CLOSED || this == CANCELLED;
  }
}
