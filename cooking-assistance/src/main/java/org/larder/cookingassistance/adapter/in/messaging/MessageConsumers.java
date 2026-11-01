package org.larder.cookingassistance.adapter.in.messaging;

import java.util.Map;

/** The consumer of each receive operation of cooking-assistance.asyncapi.yaml (coverage gate). */
public final class MessageConsumers {

  public static final Map<String, Class<?>> BY_RECEIVE_OPERATION =
      Map.of("consumeGrandmaHelpProvided", GrandmaHelpProvidedConsumer.class);

  private MessageConsumers() {}
}
