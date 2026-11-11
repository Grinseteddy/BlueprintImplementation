package org.larder.cookingassistance.domain.helprequest;

// INV-11: an ingredient request names the one ingredient it is about
/** An ingredient request that names no ingredient. */
public final class IngredientRequired extends DomainRejection {

  public IngredientRequired() {
    super("INV-11", "MISSING_FIELD", "Name the ingredient the request is about");
  }
}
