package org.larder.load;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The Grandma Avatar, another context, on the broker: it answers one request every 100 ms (10
 * messages a second) with an ingredient substitute, in the AsyncAPI shape of its answer.
 */
final class GrandmaAvatar {

  private final String recipe;
  private final String ingredient;
  private final ConcurrentLinkedQueue<String> toAnswer = new ConcurrentLinkedQueue<>();
  private final ScheduledExecutorService answering = Executors.newSingleThreadScheduledExecutor();
  private Connection broker;

  GrandmaAvatar(String recipe, String ingredient) {
    this.recipe = recipe;
    this.ingredient = ingredient;
  }

  void willAnswer(String helpRequest) {
    toAnswer.add(helpRequest);
  }

  void start() {
    try {
      ConnectionFactory factory = new ConnectionFactory();
      factory.setHost(System.getProperty("brokerHost", "localhost"));
      factory.setUsername(System.getProperty("brokerUser", "guest"));
      factory.setPassword(System.getProperty("brokerPassword", "guest"));
      broker = factory.newConnection();
      Channel channel = broker.createChannel();
      channel.exchangeDeclare("grandma-avatar", "topic", true);
      answering.scheduleAtFixedRate(() -> answerNext(channel), 100, 100, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      throw new IllegalStateException("the broker for the Grandma Avatar's answers", e);
    }
  }

  void stop() {
    answering.shutdownNow();
    try {
      broker.close();
    } catch (Exception e) {
      // the run is over; nothing depends on a clean close
    }
  }

  private void answerNext(Channel channel) {
    String id = toAnswer.poll();
    if (id == null) {
      return;
    }
    AMQP.BasicProperties properties =
        new AMQP.BasicProperties.Builder()
            .contentType("application/json")
            .headers(
                Map.of(
                    "correlationId", UUID.randomUUID().toString(),
                    "messageId", UUID.randomUUID().toString(),
                    "source", "grandma-avatar"))
            .build();
    String answer =
        """
        {"helpRequest":"%s","answerTitle":"Grandma's ideas","answer":{
         "answerType":"INGREDIENT_SUBSTITUTE","recipe":"%s","substitute":[
         {"ingredient":"%s","substituteIngredients":[{"name":"yogurt","value":200,"unit":"GRAM"}]}]}}"""
            .formatted(id, recipe, ingredient);
    try {
      channel.basicPublish(
          "grandma-avatar", "grandma-avatar.help.provided", properties, answer.getBytes(UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("publishing Grandma's answer to " + id, e);
    }
  }
}
