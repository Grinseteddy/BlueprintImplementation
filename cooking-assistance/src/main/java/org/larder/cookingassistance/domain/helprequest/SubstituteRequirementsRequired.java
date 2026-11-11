package org.larder.cookingassistance.domain.helprequest;

// INV-11: an ingredient request says what a substitute must meet (step 3 r12, S2)
/** An ingredient request without substitute requirements. */
public final class SubstituteRequirementsRequired extends DomainRejection {

  public SubstituteRequirementsRequired() {
    super("INV-11", "MISSING_FIELD", "Say what a substitute must meet");
  }
}
