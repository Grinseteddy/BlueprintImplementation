package org.larder.cookingassistance.adapter.in.messaging;

/** An answer kind of the spec a later increment builds; the message waits in the dead-letter queue. */
final class AnswerKindNotBuilt extends RuntimeException {

  AnswerKindNotBuilt(String kind) {
    super(kind + " answers are not implemented yet");
  }
}
