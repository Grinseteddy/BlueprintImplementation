package org.larder.cookingassistance.domain.helprequest;

// INV-02: a preparation-step request names one numbered step of one recipe
/** The preparation-step request names no step. */
public final class PreparationStepRequired extends DomainRejection {

  public PreparationStepRequired() {
    super("INV-02", "MISSING_FIELD", "Name the preparation step the request is about");
  }
}
