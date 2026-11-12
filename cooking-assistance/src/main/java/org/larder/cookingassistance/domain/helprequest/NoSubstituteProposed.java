package org.larder.cookingassistance.domain.helprequest;

// INV-12: an answer proposes at least one substitute, each with at least one named ingredient and
// every ingredient with its amount
/** An ingredient answer that proposes nothing. */
public final class NoSubstituteProposed extends DomainRejection {

  public NoSubstituteProposed() {
    super(
        "INV-12",
        "VALIDATION_FAILED",
        "Propose at least one substitute, each ingredient with its name and amount");
  }
}
