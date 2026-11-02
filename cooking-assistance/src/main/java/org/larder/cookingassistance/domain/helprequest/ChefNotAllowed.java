package org.larder.cookingassistance.domain.helprequest;

// INV-01: help with a preparation step goes to the Community and/or Grandma Avatar, never to a Chef
/** A Chef was asked for help other than a Menu proposal. */
public final class ChefNotAllowed extends DomainRejection {

  public ChefNotAllowed() {
    super("INV-01", "CHEF_NOT_ALLOWED", "A chef only helps with a menu proposal");
  }
}
