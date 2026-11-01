package org.larder.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.fixtures.Eventually;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Receive operations checked from their AsyncAPI examples against the running service. */
@SpringBootTest(classes = LarderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class AsyncApiContractIT {

  static final String QUEUE = "cooking-assistance.grandma-help-provided";
  static final String DEAD_LETTERS = "cooking-assistance.dlq";

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;
  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository helpRequests;

  @DisplayName("D2 the GrandmaHelpProvided example is acked (nothing pending, nothing dead-lettered); without its first required field it is dead-lettered -- B3-S02 US-CA-03 op consumeGrandmaHelpProvided")
  @Test
  void receiveExampleIsAcceptedAndBrokenOneDeadLettered() throws Exception {
    JsonNode spec = asyncApi();
    JsonNode message = spec.at("/components/messages/grandmaHelpProvided");
    JsonNode example = message.get("examples").get(0);
    String firstRequired =
        spec.at("/components/schemas/GrandmaHelpProvidedPayload/required").get(0).asText();
    theRequestTheExampleAnswersIsStored();
    admin.purgeQueue(DEAD_LETTERS, false);

    publish(example.get("headers"), example.get("payload"));

    Eventually.within(
        Duration.ofSeconds(10),
        () -> assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).hasSize(1));
    assertThat(messageCount(QUEUE)).isZero();
    assertThat(messageCount(DEAD_LETTERS)).isZero();

    ObjectNode broken = example.get("payload").deepCopy();
    broken.remove(firstRequired);
    ObjectNode otherMessage = example.get("headers").deepCopy();
    otherMessage.put("messageId", "7182a3b4-b5c6-4d7e-8f01-6c7d8e9fa0b1");
    publish(otherMessage, broken);

    Eventually.within(Duration.ofSeconds(10), () -> assertThat(messageCount(DEAD_LETTERS)).isEqualTo(1));
  }

  private void theRequestTheExampleAnswersIsStored() {
    jdbc.sql("delete from cooking_assistance.processed_message").update();
    jdbc.sql("delete from cooking_assistance.outbox").update();
    jdbc.sql("delete from cooking_assistance.help").update();
    jdbc.sql("delete from cooking_assistance.asked_provider").update();
    jdbc.sql("delete from cooking_assistance.help_request").update();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  /** The test plays the Grandma Avatar context: it owns the grandma-avatar exchange. */
  private void publish(JsonNode headers, JsonNode payload) {
    admin.declareExchange(new TopicExchange("grandma-avatar", true, false));
    MessageProperties properties = new MessageProperties();
    properties.setContentType("application/json");
    headers.fields().forEachRemaining(header -> properties.setHeader(header.getKey(), header.getValue().asText()));
    rabbit.send(
        "grandma-avatar",
        "grandma-avatar.help.provided",
        new Message(payload.toString().getBytes(StandardCharsets.UTF_8), properties));
  }

  private int messageCount(String queue) {
    Properties properties = admin.getQueueProperties(queue);
    return (Integer) properties.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);
  }

  private static JsonNode asyncApi() throws Exception {
    try (InputStream in =
        AsyncApiContractIT.class.getClassLoader().getResourceAsStream("api/cooking-assistance.asyncapi.yaml")) {
      return new ObjectMapper(new YAMLFactory()).readTree(in);
    }
  }
}
