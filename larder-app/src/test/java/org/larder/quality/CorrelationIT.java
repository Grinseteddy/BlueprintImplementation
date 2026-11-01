package org.larder.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** QS-14 short check: one help journey correlates HTTP and messages (ADR0020). */
@SpringBootTest(classes = LarderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class CorrelationIT {

  @Value("${local.server.port}")
  int port;

  @Autowired AmqpAdmin admin;
  @Autowired RabbitTemplate rabbit;

  @DisplayName("E2 POST /help-requests -> the returned helpRequestId is the correlationId header of the published HelpRequestCreated -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void httpRequestAndPublishedMessageShareTheCorrelation() throws Exception {
    String queue = "quality.correlation";
    admin.declareQueue(new Queue(queue, false, false, false));
    admin.declareBinding(
        BindingBuilder.bind(new Queue(queue))
            .to(new TopicExchange("cooking-assistance"))
            .with("cooking-assistance.help-request.created"));
    admin.purgeQueue(queue, false);

    String created =
        RestClient.create("http://localhost:" + port)
            .post()
            .uri("/help-requests")
            .header("Authorization", TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer())
            .header("version", "1.0.4")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                """
                {"title":"Sauce splits","type":"PREPARATION_STEP_EXPLANATION","description":"Step 4",
                 "recipe":"%s","howToStep":"%s","preferredProvider":["COMMUNITY"]}"""
                    .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value()))
            .retrieve()
            .body(String.class);
    String helpRequestId = new ObjectMapper().readTree(created).get("helpRequestId").asText();

    Message published = rabbit.receive(queue, 10_000);
    assertThat(published).isNotNull();
    assertThat(published.getMessageProperties().getHeaders()).containsEntry("correlationId", helpRequestId);
  }
}
