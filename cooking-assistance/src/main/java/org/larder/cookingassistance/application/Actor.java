package org.larder.cookingassistance.application;

import java.util.Set;
import org.larder.cookingassistance.domain.helprequest.CookId;

/** The authenticated caller as the application sees it (ADR0012): never a framework type. */
public record Actor(CookId id, Set<Role> roles, boolean premium) {

  /** ADR0013, deny by default: the actor must hold the role to issue the command. */
  public void require(Role role, String command) {
    if (!roles.contains(role)) {
      throw new NotAuthorized(command, role);
    }
  }
}
