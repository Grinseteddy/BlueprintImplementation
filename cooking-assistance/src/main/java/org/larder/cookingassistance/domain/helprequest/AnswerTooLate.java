package org.larder.cookingassistance.domain.helprequest;

// INV-09: only an answer at or before the provider's deadline counts (step 3 r5)
/** An asked provider answered after their deadline. */
public final class AnswerTooLate extends DomainRejection {

  public AnswerTooLate() {
    super("INV-09", "INVALID_STATE", "The answer came after the provider's deadline");
  }
}
