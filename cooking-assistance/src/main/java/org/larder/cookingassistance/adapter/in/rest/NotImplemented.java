package org.larder.cookingassistance.adapter.in.rest;

/** An operation or variant of the spec a later increment builds; answered with 501. */
final class NotImplemented extends RuntimeException {

  NotImplemented(String what) {
    super(what + " is not implemented yet");
  }
}
