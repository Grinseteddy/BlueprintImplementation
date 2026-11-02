package org.larder.cookingassistance.adapter.out.messaging;

import java.util.Map;
import java.util.UUID;

/** A message ready for the broker: the spec's channel address, its headers and JSON payload. */
public record OutgoingMessage(
    String channel, UUID messageId, Map<String, String> headers, String payload) {}
