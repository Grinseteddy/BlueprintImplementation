package org.larder.cookingassistance.adapter.out.messaging;

/**
 * Port to the broker (ADR0015): returns only when the broker confirmed the message, throws
 * otherwise. The AMQP adapter with publisher confirms comes in step 12.
 */
public interface MessagePublisher {

  void publish(OutgoingMessage message);
}
