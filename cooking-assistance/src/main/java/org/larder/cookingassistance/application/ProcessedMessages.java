package org.larder.cookingassistance.application;

import java.util.UUID;

/**
 * Records which consumed messages were processed, so a redelivered message has no effect
 * (ADR0015). Called inside the consumer's unit of work.
 */
public interface ProcessedMessages {

  /** Records the message id; true when it was seen for the first time, false when repeated. */
  boolean recordFirstProcessing(UUID messageId);
}
