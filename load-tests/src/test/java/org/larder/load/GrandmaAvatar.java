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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Grandma Avatar, another context, on the broker: it answers one request every 100 ms (10
 * messages a second) with an ingredient substitute or a catastrophe mitigation, in the AsyncAPI
 * shape of its answer.
 */
final class GrandmaAvatar {

  private final String recipe;
  private final String ingredient;
  private final String catastropheRecipe;

  /** The answers still to publish, as message payloads. */
  private final ConcurrentLinkedQueue<String> toAnswer = new ConcurrentLinkedQueue<>();

  private final ScheduledExecutorService answering = Executors.newSingleThreadScheduledExecutor();
  private final AtomicInteger failedAnswers = new AtomicInteger();
  private Connection broker;

  GrandmaAvatar(String recipe, String ingredient, String catastropheRecipe) {
    this.recipe = recipe;
    this.ingredient = ingredient;
    this.catastropheRecipe = catastropheRecipe;
  }

  /** An ingredient request: Grandma proposes a substitute. */
  void willAnswer(String helpRequest) {
    toAnswer.add(
        """
        {"helpRequest":"%s","answerTitle":"Grandma's ideas","answer":{
         "answerType":"INGREDIENT_SUBSTITUTE","recipe":"%s","substitute":[
         {"ingredient":"%s","substituteIngredients":[{"name":"yogurt","value":200,"unit":"GRAM"}]}]}}"""
            .formatted(helpRequest, recipe, ingredient));
  }

  /** A catastrophe request: Grandma tells how to save the dish of its recipe (INV-37). */
  void willMitigate(String helpRequest) {
    toAnswer.add(
        """
        {"helpRequest":"%s","answerTitle":"Save the chicken","answer":{
         "answerType":"STEPS_TO_MITIGATE_CATASTROPHE","recipe":"%s",
         "explanation":"cut off the burnt part, make a fresh sauce"}}"""
            .formatted(helpRequest, catastropheRecipe));
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
    System.err.println("Grandma's answers not published: " + failedAnswers.get());
    try {
      broker.close();
    } catch (Exception e) {
      // the run is over; nothing depends on a clean close
    }
  }

  private void answerNext(Channel channel) {
    String answer = toAnswer.poll();
    if (answer == null) {
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
    try {
      channel.basicPublish(
          "grandma-avatar", "grandma-avatar.help.provided", properties, answer.getBytes(UTF_8));
    } catch (Exception e) {
      // a task that throws is never scheduled again: count the failure and answer the next one
      failedAnswers.incrementAndGet();
      System.err.println("Grandma's answer was not published: " + e + " — " + answer);
    }
  }
}
