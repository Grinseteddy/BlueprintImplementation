package org.larder.cookingassistance.application;

/** The caller may not issue this command (ADR0013, deny by default); mapped to 403 in step 9. */
public final class NotAuthorized extends RuntimeException {

  public NotAuthorized(String command, Role requiredRole) {
    super(command + " needs role " + requiredRole);
  }
}
