package org.larder.cookingassistance.domain.helprequest;

// INV-06: a rejection says what didn't work (step 3 r5)
/** A Help was rejected without a note. */
public final class RejectionNoteRequired extends DomainRejection {

  public RejectionNoteRequired() {
    super("INV-06", "MISSING_FIELD", "Say what didn't work");
  }
}
