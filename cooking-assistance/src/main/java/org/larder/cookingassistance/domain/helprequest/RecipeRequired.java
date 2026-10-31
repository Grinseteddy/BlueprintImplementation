package org.larder.cookingassistance.domain.helprequest;

// INV-02: a preparation-step request names one numbered step of one recipe
/** The help request names no recipe although its type requires one. */
public final class RecipeRequired extends DomainRejection {

  public RecipeRequired() {
    super("INV-02", "MISSING_FIELD", "Name the recipe the request is about");
  }
}
