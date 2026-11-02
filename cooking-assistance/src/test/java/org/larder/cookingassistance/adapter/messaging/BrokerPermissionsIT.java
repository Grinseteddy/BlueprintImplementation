package org.larder.cookingassistance.adapter.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.out.messaging.MessagePublisher;
import org.larder.cookingassistance.adapter.out.messaging.OutgoingMessage;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * The module runs with exactly the broker permissions ADR0012 names, and those permissions do not
 * let it act as the Grandma Avatar (security review SEC-04).
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@Import(PostgresTestContainer.class)
class BrokerPermissionsIT {

  static final String USER = "cooking-assistance";
  static final String PASSWORD = "test-only";
  static final RabbitMQContainer RABBIT = brokerWithTheModulesUser();

  @DynamicPropertySource
  static void connectAsTheModulesUser(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", () -> USER);
    registry.add("spring.rabbitmq.password", () -> PASSWORD);
  }

  /** ADR0012 addition: configure and write on what the module owns, read to bind from Grandma. */
  private static RabbitMQContainer brokerWithTheModulesUser() {
    RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management-alpine");
    rabbit.start();
    try {
      exec(rabbit, "rabbitmqctl", "add_user", USER, PASSWORD);
      exec(
          rabbit,
          "rabbitmqctl",
          "set_permissions",
          "-p",
          "/",
          USER,
          "^cooking-assistance(\\..*)?$",
          "^cooking-assistance(\\..*)?$",
          "^(cooking-assistance\\..*|grandma-avatar)$");
      // the Grandma Avatar context owns and declares its exchange
      exec(
          rabbit,
          "rabbitmqadmin",
          "declare",
          "exchange",
          "name=grandma-avatar",
          "type=topic",
          "durable=true");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return rabbit;
  }

  private static void exec(RabbitMQContainer rabbit, String... command) throws Exception {
    var result = rabbit.execInContainer(command);
    if (result.getExitCode() != 0) {
      throw new IllegalStateException(String.join(" ", command) + ": " + result.getStderr());
    }
  }

  @Autowired RabbitAdmin modulesAdmin;
  @Autowired MessagePublisher publisher;

  @DisplayName(
      "module's broker user with exactly the ADR0012 permissions -> its declarations succeed, HelpRequestCreated is confirmed and delivered; a publish to grandma-avatar is refused -- B3-S02 US-CA-03 OWASP-A06 ADR0012")
  @Test
  void theModuleRunsWithItsOwnPermissionsOnly() throws Exception {
    modulesAdmin.initialize();
    assertThat(modulesAdmin.getQueueInfo("cooking-assistance.grandma-help-provided")).isNotNull();

    CachingConnectionFactory owner =
        new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    owner.setUsername(RABBIT.getAdminUsername());
    owner.setPassword(RABBIT.getAdminPassword());
    try {
      RabbitAdmin ownersAdmin = new RabbitAdmin(owner);
      Queue probe = ownersAdmin.declareQueue();
      ownersAdmin.declareBinding(
          BindingBuilder.bind(probe)
              .to(new TopicExchange("cooking-assistance"))
              .with("cooking-assistance.help-request.created"));

      publisher.publish(created());

      Message received = new RabbitTemplate(owner).receive(probe.getName(), 5_000);
      assertThat(received).isNotNull();
    } finally {
      owner.destroy();
    }

    com.rabbitmq.client.ConnectionFactory asTheModule = new com.rabbitmq.client.ConnectionFactory();
    asTheModule.setHost(RABBIT.getHost());
    asTheModule.setPort(RABBIT.getAmqpPort());
    asTheModule.setUsername(USER);
    asTheModule.setPassword(PASSWORD);
    try (Connection connection = asTheModule.newConnection()) {
      Channel channel = connection.createChannel();
      channel.confirmSelect();
      channel.basicPublish(
          "grandma-avatar",
          "grandma-avatar.help.provided",
          null,
          "{}".getBytes(StandardCharsets.UTF_8));
      assertThatThrownBy(() -> channel.waitForConfirmsOrDie(5_000))
          .hasStackTraceContaining("ACCESS_REFUSED");
    }
  }

  private static OutgoingMessage created() {
    UUID id = UUID.fromString("8c6894b6-9321-3924-ac9a-3d6f6d4c5aa7");
    return new OutgoingMessage(
        "cooking-assistance.help-request.created",
        id,
        Map.of(
            "messageId", id.toString(),
            "correlationId", "23a8eeed-35f6-460b-892e-7bb458a8fded",
            "source", "cooking-assistance"),
        "{\"helpRequestId\":\"23a8eeed-35f6-460b-892e-7bb458a8fded\"}");
  }
}
