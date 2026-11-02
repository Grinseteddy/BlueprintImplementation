package org.larder.cookingassistance.application.fakes;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.larder.cookingassistance.application.ProcessedMessages;

/** In-memory ProcessedMessages for use-case tests; passes ProcessedMessagesContract. */
public class InMemoryProcessedMessages implements ProcessedMessages {

  private final Set<UUID> processed = new HashSet<>();

  @Override
  public boolean recordFirstProcessing(UUID messageId) {
    return processed.add(messageId);
  }
}
