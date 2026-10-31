package org.larder.cookingassistance.domain.helprequest;

// Lifecycle (design brief §3): CLOSED and CANCELLED requests accept no command
/** The command is not allowed in the Help Request's current status. */
public final class InvalidHelpRequestState extends DomainRejection {

  public InvalidHelpRequestState(HelpRequestStatus status) {
    super("LIFECYCLE", "INVALID_STATE", "Not allowed while the help request is " + status);
  }
}
