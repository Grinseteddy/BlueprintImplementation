package org.larder.cookingassistance.adapter.out.messaging;

import java.util.List;

/** A message that breaks the approved AsyncAPI spec; retrying cannot fix it. */
final class InvalidOutgoingMessage extends RuntimeException {

  InvalidOutgoingMessage(List<String> violations) {
    super(String.join("; ", violations));
  }
}
