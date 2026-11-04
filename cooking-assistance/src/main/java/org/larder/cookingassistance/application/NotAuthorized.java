package org.larder.cookingassistance.application;

/** The caller may not issue this command (ADR0013, deny by default); mapped to 403 in step 9. */
public final class NotAuthorized extends RuntimeException {

  public NotAuthorized(String command, Role requiredRole) {
    this(command + " needs role " + requiredRole);
  }

  private NotAuthorized(String message) {
    super(message);
  }

  /** ADR0013: the command is for the help request's requester only. */
  public static NotAuthorized requesterOnly(String command) {
    return new NotAuthorized(command + " is for the requester only");
  }
}
