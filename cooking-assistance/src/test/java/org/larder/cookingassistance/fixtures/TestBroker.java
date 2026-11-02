package org.larder.cookingassistance.fixtures;

import org.testcontainers.containers.RabbitMQContainer;

/** RabbitMQ for the slow suites, with what other contexts own already declared (ADR0012). */
public final class TestBroker {

  private TestBroker() {}

  /** A started broker on which the Grandma Avatar has declared its exchange, as its owner would. */
  public static RabbitMQContainer withGrandmaAvatarsExchange() {
    RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    rabbit.start();
    try {
      var result =
          rabbit.execInContainer(
              "rabbitmqadmin",
              "declare",
              "exchange",
              "name=grandma-avatar",
              "type=topic",
              "durable=true");
      if (result.getExitCode() != 0) {
        throw new IllegalStateException("grandma-avatar not declared: " + result.getStderr());
      }
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return rabbit;
  }
}
