package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Steps of US-CA-03 in the domain language, through the driver. */
public class HelpRequestSteps {

  private final AmqpAdmin admin;
  private final RabbitTemplate rabbit;
  private final JdbcClient jdbc;
  private final Environment environment;
  private AcceptanceDriver driver;
  private String createdQueue;
  private ResponseEntity<String> lastResponse;

  public HelpRequestSteps(
      AmqpAdmin admin, RabbitTemplate rabbit, JdbcClient jdbc, Environment environment) {
    this.admin = admin;
    this.rabbit = rabbit;
    this.jdbc = jdbc;
    this.environment = environment;
  }

  /** Only the test's own setup touches the database: each scenario starts empty. */
  @Before
  public void emptyState() {
    driver =
        new AcceptanceDriver(
            admin, rabbit, Integer.parseInt(environment.getRequiredProperty("local.server.port")));
    jdbc.sql("delete from cooking_assistance.processed_message").update();
    jdbc.sql("delete from cooking_assistance.outbox").update();
    jdbc.sql("delete from cooking_assistance.help").update();
    jdbc.sql("delete from cooking_assistance.asked_provider").update();
    jdbc.sql("delete from cooking_assistance.help_request").update();
    createdQueue = driver.listenTo(AcceptanceDriver.CREATED);
  }

  @When("Lena asks Grandma Avatar about step {int} of her Hollandaise")
  public void lenaAsksGrandma(int step) {
    lastResponse = driver.post("/help-requests", HelpRequests.LENA, "cook", request("[\"GRANDMA_AVATAR\"]"));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("Grandma Avatar receives the request about step {int}")
  public void grandmaReceivesTheRequest(int step) {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType")).containsExactly("GRANDMA_AVATAR");
    assertThat(created.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
  }

  private static String request(String providers) {
    return """
        {"title":"How do I keep the sauce from splitting?","type":"PREPARATION_STEP_EXPLANATION",
         "description":"Step 4 of my Hollandaise keeps splitting","recipe":"%s","howToStep":"%s",
         "preferredProvider":%s}"""
        .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value(), providers);
  }
}
