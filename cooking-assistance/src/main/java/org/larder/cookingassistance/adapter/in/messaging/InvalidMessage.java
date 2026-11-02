package org.larder.cookingassistance.adapter.in.messaging;

import java.util.List;

/** The message does not match the approved AsyncAPI spec; it can never succeed. */
final class InvalidMessage extends RuntimeException {

  InvalidMessage(List<String> violations) {
    super(String.join("; ", violations));
  }
}
