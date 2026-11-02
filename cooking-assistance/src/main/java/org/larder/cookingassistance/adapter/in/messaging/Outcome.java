package org.larder.cookingassistance.adapter.in.messaging;

/** What the transport does with a consumed message (ADR0015). */
public enum Outcome {
  /** Processed, or a redelivery without effect: acknowledge. */
  ACK,
  /**
   * Can never succeed (invalid, unknown request, rejected, answer kind not built yet): move to the
   * dead-letter queue.
   */
  DEAD_LETTER,
  /** May succeed later: deliver again, up to the delivery limit. */
  RETRY
}
