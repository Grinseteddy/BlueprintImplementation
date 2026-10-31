package org.larder.cookingassistance.application;

/** Roles of a caller, from the token's claims (ADR0013). */
public enum Role {
  COOK,
  CHEF,
  COMMUNITY
}
