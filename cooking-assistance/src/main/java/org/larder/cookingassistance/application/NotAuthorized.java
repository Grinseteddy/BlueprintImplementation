package org.larder.cookingassistance.application;

/** The caller may not issue this command (ADR0013, deny by default); mapped to 403 in step 9. */
public final class NotAuthorized extends RuntimeException {

  private final String code;

  public NotAuthorized(String command, Role requiredRole) {
    this("NOT_AUTHORIZED", command + " needs role " + requiredRole);
  }

  private NotAuthorized(String code, String message) {
    super(message);
    this.code = code;
  }

  /** ADR0013: the command is for the help request's requester only. */
  public static NotAuthorized requesterOnly(String command) {
    return new NotAuthorized("NOT_REQUESTER", command + " is for the requester only");
  }

  /** The API's error code for this refusal (cooking-assistance.openapi.yaml). */
  public String code() {
    return code;
  }
}
