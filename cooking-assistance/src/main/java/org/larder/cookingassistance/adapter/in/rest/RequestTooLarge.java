package org.larder.cookingassistance.adapter.in.rest;

/** The request body is larger than ADR0019 allows; answered with 413 PAYLOAD_TOO_LARGE. */
final class RequestTooLarge extends RuntimeException {

  RequestTooLarge() {
    super("The request body is larger than 1 MB");
  }
}
