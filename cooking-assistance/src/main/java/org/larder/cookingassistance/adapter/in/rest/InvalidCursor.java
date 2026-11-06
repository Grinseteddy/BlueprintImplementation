package org.larder.cookingassistance.adapter.in.rest;

/** A cursor this API did not issue; answered with 400 VALIDATION_FAILED (ADR0016 L6). */
final class InvalidCursor extends RuntimeException {

  InvalidCursor() {
    super("The cursor was not issued by this API");
  }
}
