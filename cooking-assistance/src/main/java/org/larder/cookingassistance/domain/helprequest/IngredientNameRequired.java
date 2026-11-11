package org.larder.cookingassistance.domain.helprequest;

// INV-11: an ingredient request names its ingredient, as the client sent it (step 3 r15)
/** An ingredient request without the ingredient's name. */
public final class IngredientNameRequired extends DomainRejection {

  public IngredientNameRequired() {
    super("INV-11", "MISSING_FIELD", "Name the ingredient to substitute");
  }
}
