package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.Before;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.CookId;
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

  @When("Lena asks the Community and Grandma Avatar about step {int} of her Hollandaise")
  public void lenaAsksCommunityAndGrandma(int step) {
    lastResponse =
        driver.post(
            "/help-requests", HelpRequests.LENA, "cook", request("[\"COMMUNITY\",\"GRANDMA_AVATAR\"]"));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the Community and Grandma Avatar receive the same request")
  public void bothReceiveTheSameRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
  }

  static final CookId TOM = new CookId(UUID.fromString("6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d"));
  static final UUID BREAD = UUID.fromString("9e8d7c6b-5a49-4382-9716-05f4e3d2c1b0");
  static final UUID BREAD_STEP_2 = UUID.fromString("1f2e3d4c-5b6a-4798-8b7a-6c5d4e3f2a1b");

  @When("Tom, on the free plan, asks the Community about step {int} of his bread")
  public void tomAsksTheCommunity(int step) {
    lastResponse =
        driver.post(
            "/help-requests",
            TOM,
            "cook",
            """
            {"title":"Why does my dough not rise?","type":"PREPARATION_STEP_EXPLANATION",
             "description":"Step 2 of my bread","recipe":"%s","howToStep":"%s",
             "preferredProvider":["COMMUNITY"]}"""
                .formatted(BREAD, BREAD_STEP_2));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the request is sent to the Community")
  public void requestIsSentToTheCommunity() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("requester").asText()).isEqualTo(TOM.value().toString());
    assertThat(created.get("askedProviders").findValuesAsText("providerType")).containsExactly("COMMUNITY");
  }

  @When("Lena asks chef Marco about step {int} of her Hollandaise")
  public void lenaAsksChefMarco(int step) {
    lastResponse = driver.post("/help-requests", HelpRequests.LENA, "cook", request("[\"CHEF\"]"));
  }

  @Then("the request is refused because a chef only helps with a menu proposal")
  public void refusedBecauseChefOnlyForMenus() {
    // OpenAPI 1.0.2 (step 4 r1): the schema forbids a chef outside a menu request → 400
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
    assertThat(lastResponse.getBody()).contains("\"code\":\"VALIDATION_FAILED\"");
  }

  @Then("nobody receives a request")
  public void nobodyReceivesARequest() {
    driver.noMessage(createdQueue);
  }

  static final UUID STEP_6 = UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-2a3b4c5d6e7f");
  private final java.util.List<String> lenasRequests = new java.util.ArrayList<>();

  private String lenaAsksTheCommunityAbout(UUID step, String question, String extra) {
    ResponseEntity<String> created =
        driver.post(
            "/help-requests",
            HelpRequests.LENA,
            "cook",
            """
            {"title":"%s","type":"PREPARATION_STEP_EXPLANATION",
             "description":"My Hollandaise keeps splitting","recipe":"%s","howToStep":"%s",
             "preferredProvider":["COMMUNITY"]%s}"""
                .formatted(question, HelpRequests.HOLLANDAISE.value(), step, extra));
    lastResponse = created;
    if (created.getStatusCode().value() == 201) {
      lenasRequests.add(driver.json(created).get("helpRequestId").asText());
    }
    return created.getBody();
  }

  private JsonNode readRequest(String helpRequestId) {
    ResponseEntity<String> read = driver.get("/help-requests/" + helpRequestId, HelpRequests.JONAS, "cook,community");
    assertThat(read.getStatusCode().value()).isEqualTo(200);
    return driver.json(read);
  }

  @io.cucumber.java.en.Given("Lena asked the Community about step {int} of her Hollandaise")
  public void lenaAskedTheCommunity(int step) {
    lenaAsksTheCommunityAbout(HelpRequests.STEP_4.value(), "Sauce splits", "");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("Lena asks the Community {string} about step {int} of her Hollandaise")
  public void lenaAsksTheCommunityAQuestion(String question, int step) {
    lenaAsksTheCommunityAbout(HelpRequests.STEP_4.value(), question, "");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her request shows the Hollandaise, step {int} and her question")
  public void requestShowsRecipeStepAndQuestion(int step) {
    JsonNode request = readRequest(lenasRequests.getLast());
    assertThat(request.get("recipe").asText()).isEqualTo(HelpRequests.HOLLANDAISE.value().toString());
    assertThat(request.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
    assertThat(request.get("title").asText()).isEqualTo("How do I keep the sauce from splitting?");
  }

  @When("Lena asks the Community about step {int} and about step {int} of her Hollandaise")
  public void lenaAsksAboutTwoSteps(int first, int second) {
    lenaAsksTheCommunityAbout(HelpRequests.STEP_4.value(), "Step 4", "");
    lenaAsksTheCommunityAbout(STEP_6, "Step 6", "");
  }

  @Then("she has two requests, one about step {int} and one about step {int}")
  public void twoRequestsOneStepEach(int first, int second) {
    assertThat(lenasRequests).hasSize(2);
    assertThat(readRequest(lenasRequests.get(0)).get("howToStep").asText())
        .isEqualTo(HelpRequests.STEP_4.value().toString());
    assertThat(readRequest(lenasRequests.get(1)).get("howToStep").asText()).isEqualTo(STEP_6.toString());
  }

  @When("she tries to add step {int} to that request")
  public void sheTriesToAddAStep(int step) {
    lastResponse =
        driver.send(
            org.springframework.http.HttpMethod.PATCH,
            "/help-requests/" + lenasRequests.getLast(),
            HelpRequests.LENA,
            "cook",
            "{\"howToStep\":\"" + STEP_6 + "\"}");
  }

  @Then("nothing is added: the request is still about step {int} only")
  public void nothingIsAdded(int step) {
    // the API offers no operation that changes a request (OpenAPI: no PATCH)
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(405);
    assertThat(readRequest(lenasRequests.getLast()).get("howToStep").asText())
        .isEqualTo(HelpRequests.STEP_4.value().toString());
  }

  @When("Lena asks the Community about step {int} with a photo of the split sauce")
  public void lenaAsksWithAPhoto(int step) {
    lenaAsksTheCommunityAbout(
        HelpRequests.STEP_4.value(),
        "Sauce splits",
        ",\"pictures\":[\"https://larder.org/media/images/split-sauce\"]");
  }

  @Then("the request with the photo is refused")
  public void requestWithPhotoIsRefused() {
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
  }

  @io.cucumber.java.en.But("the same request without the photo is accepted")
  public void sameRequestWithoutPhotoIsAccepted() {
    lenaAsksTheCommunityAbout(HelpRequests.STEP_4.value(), "Sauce splits", "");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  private static String request(String providers) {
    return """
        {"title":"How do I keep the sauce from splitting?","type":"PREPARATION_STEP_EXPLANATION",
         "description":"Step 4 of my Hollandaise keeps splitting","recipe":"%s","howToStep":"%s",
         "preferredProvider":%s}"""
        .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value(), providers);
  }
}
