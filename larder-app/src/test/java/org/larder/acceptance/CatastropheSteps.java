package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.larder.acceptance.HelpRequestAssertions.assertDeadline;
import static org.larder.acceptance.HelpRequestAssertions.assertShowsThePreviousNote;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;

/** Steps of US-CA-04 in the domain language, through the driver. */
public class CatastropheSteps {

  private final AmqpAdmin admin;
  private final RabbitTemplate rabbit;
  private final Environment environment;
  private final AcceptanceClock clock;
  private AcceptanceDriver driver;
  private String createdQueue;
  private String providedQueue;
  private String acceptedQueue;
  private String rejectedQueue;
  private String cancelledQueue;
  private String timedOutQueue;

  /** Lena's requests in the order she made them; the last one is "her catastrophe request". */
  private final List<String> lenasRequests = new ArrayList<>();

  private ResponseEntity<String> lastResponse;

  /** The Help Grandma Avatar sent for Lena's request ("Grandma's answer"). */
  private String grandmasHelp;

  /** The Help Jonas sent from the Community for Lena's request ("Jonas's answer"). */
  private String jonassHelp;

  public CatastropheSteps(
      AmqpAdmin admin, RabbitTemplate rabbit, Environment environment, AcceptanceClock clock) {
    this.admin = admin;
    this.rabbit = rabbit;
    this.environment = environment;
    this.clock = clock;
  }

  /** The story's Saturday, 14 November 2026, at the given time in Berlin (CET). */
  static Instant saturday(String time) {
    return LocalDateTime.parse("2026-11-14T" + time).atZone(ZoneId.of("Europe/Berlin")).toInstant();
  }

  /**
   * After the common reset of every scenario (empty tables, queues, the clock): the story starts on
   * Saturday at 18:00, before the first time an example names (R1.E1, Saturday 18:40).
   */
  @Before(value = "@US-CA-04", order = 20_000)
  public void saturdayEvening() {
    clock.moveTo(saturday("18:00"));
    driver =
        new AcceptanceDriver(
            admin, rabbit, Integer.parseInt(environment.getRequiredProperty("local.server.port")));
    createdQueue = driver.listenTo(AcceptanceDriver.CREATED);
    providedQueue = driver.listenTo(AcceptanceDriver.PROVIDED);
    acceptedQueue = driver.listenTo(AcceptanceDriver.ACCEPTED);
    rejectedQueue = driver.listenTo(AcceptanceDriver.REJECTED);
    cancelledQueue = driver.listenTo(AcceptanceDriver.CANCELLED);
    timedOutQueue = driver.listenTo(AcceptanceDriver.TIMED_OUT);
  }

  // Lena's actions, in the domain language

  /** Lena sends a request; a created one becomes "her catastrophe request". */
  private void lenaAsks(String request) {
    lastResponse = driver.post("/help-requests", HelpRequests.LENA, "cook", request);
    if (lastResponse.getStatusCode().value() == 201) {
      lenasRequests.add(driver.json(lastResponse).get("helpRequestId").asText());
    }
  }

  /** What happened to Lena's roast chicken, unless a scenario names it (R2.E1). */
  static final String BURNT_ON_THE_BOTTOM = "Roast chicken burnt on the bottom, guests at 19:00";

  /** Lena asks the given providers about her burnt roast chicken, with her recipe. */
  private void lenaAsksAboutBurntChicken(String... providerTypes) {
    lenaAsksWithHerRecipe(BURNT_ON_THE_BOTTOM, List.of(), providerTypes);
  }

  /** Lena's roast chicken request with her recipe and the given photos, none for an empty list. */
  private void lenaAsksWithHerRecipe(
      String description, List<String> pictures, String... providerTypes) {
    lenaAsks(
        """
        {"title":"My roast chicken is burnt","type":"STEPS_TO_MITIGATE_CATASTROPHE",
         "description":"%s","recipe":"%s"%s,"preferredProvider":%s}"""
            .formatted(
                description,
                HelpRequests.ROAST_CHICKEN.value(),
                pictures.isEmpty()
                    ? ""
                    : pictures.stream()
                        .map(picture -> "\"" + picture + "\"")
                        .collect(Collectors.joining(",", ",\"pictures\":[", "]")),
                providers(providerTypes)));
  }

  /** The photo Lena takes of the burnt bottom (R3.E1), the first of the spec example's two. */
  static final String PHOTO_OF_THE_BURNT_BOTTOM = "https://img.larder.example/burnt-chicken-1.jpg";

  private static List<String> texts(JsonNode array) {
    List<String> texts = new ArrayList<>();
    array.forEach(element -> texts.add(element.asText()));
    return texts;
  }

  /** Lena asks the given providers about her burnt roast chicken, without a recipe (R2.E3). */
  private void lenaAsksAboutBurntChickenWithoutRecipe(String... providerTypes) {
    lenaAsksWithoutRecipe("My roast chicken is burnt", BURNT_ON_THE_BOTTOM, providerTypes);
  }

  /** Lena asks the given providers about a catastrophe, without a recipe. */
  private void lenaAsksWithoutRecipe(String title, String description, String... providerTypes) {
    lenaAsks(
        """
        {"title":"%s","type":"STEPS_TO_MITIGATE_CATASTROPHE",
         "description":"%s","preferredProvider":%s}"""
            .formatted(title, description, providers(providerTypes)));
  }

  private String lenasRequest() {
    return lenasRequests.getLast();
  }

  /** Jonas from the Community reads a request, as a provider would. */
  private JsonNode readRequest(String helpRequestId) {
    ResponseEntity<String> read =
        driver.get("/help-requests/" + helpRequestId, HelpRequests.JONAS, "cook,community");
    assertThat(read.getStatusCode().value()).isEqualTo(200);
    return driver.json(read);
  }

  /** The recipes the examples name, by their name. */
  private static final Map<String, RecipeId> RECIPES =
      Map.of("Roast chicken", HelpRequests.ROAST_CHICKEN);

  private static RecipeId recipe(String name) {
    return Optional.ofNullable(RECIPES.get(name))
        .orElseThrow(() -> new IllegalArgumentException("no recipe named " + name));
  }

  private static String providers(String... providerTypes) {
    return Arrays.stream(providerTypes)
        .map(type -> "\"" + type + "\"")
        .collect(Collectors.joining(",", "[", "]"));
  }

  // R1

  @When("on Saturday at 18:40 Lena asks Grandma Avatar about her burnt roast chicken")
  public void lenaAsksGrandmaAt1840() {
    clock.moveTo(saturday("18:40"));
    lenaAsksAboutBurntChicken("GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("Grandma Avatar receives the catastrophe request")
  public void grandmaReceivesTheCatastropheRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("GRANDMA_AVATAR");
    assertThat(created.get("title").asText()).isEqualTo("My roast chicken is burnt");
  }

  @When("Lena asks the Community and Grandma Avatar about her burnt roast chicken")
  public void lenaAsksBoth() {
    lenaAsksAboutBurntChicken("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the Community and Grandma Avatar receive the same catastrophe request")
  public void bothReceiveTheSameCatastropheRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(created.get("title").asText()).isEqualTo("My roast chicken is burnt");
  }

  @When("Tom, on the free plan, asks the Community about his collapsed soufflé")
  public void tomAsksTheCommunity() {
    lastResponse =
        driver.post(
            "/help-requests",
            HelpRequestSteps.TOM,
            "cook",
            """
            {"title":"My soufflé collapsed","type":"STEPS_TO_MITIGATE_CATASTROPHE",
             "description":"It fell flat when I opened the oven",
             "preferredProvider":["COMMUNITY"]}""");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the catastrophe request is sent to the Community")
  public void catastropheRequestIsSentToTheCommunity() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("requester").asText())
        .isEqualTo(HelpRequestSteps.TOM.value().toString());
    assertThat(created.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("COMMUNITY");
  }

  @When("Lena asks chef Marco about her burnt roast chicken")
  public void lenaAsksChefMarco() {
    lenaAsksAboutBurntChicken("CHEF");
  }

  @Then("the catastrophe request is refused")
  public void catastropheRequestIsRefused() {
    // the schema's else branch: only a menu request may name a chef → 400 (step 9)
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
    assertThat(lastResponse.getBody()).contains("\"code\":\"VALIDATION_FAILED\"");
  }

  @Then("no catastrophe request is sent")
  public void noCatastropheRequestIsSent() {
    driver.noMessage(createdQueue);
  }

  // R2

  @When("Lena asks for help: {string}")
  public void lenaAsksForHelp(String whatHappened) {
    lenaAsksWithHerRecipe(whatHappened, List.of(), "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her catastrophe request shows the dish and what happened")
  public void requestShowsTheDishAndWhatHappened() {
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(request.get("title").asText()).isEqualTo("My roast chicken is burnt");
    assertThat(request.get("description").asText()).isEqualTo(BURNT_ON_THE_BOTTOM);
  }

  @When("Lena asks for help with her burnt roast chicken and adds her recipe {string}")
  public void lenaAddsHerRecipe(String recipeName) {
    assertThat(recipe(recipeName)).isEqualTo(HelpRequests.ROAST_CHICKEN);
    lenaAsksAboutBurntChicken("COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her catastrophe request shows the recipe {string}")
  public void requestShowsTheRecipe(String recipeName) {
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.path("recipe").asText()).isEqualTo(recipe(recipeName).value().toString());
  }

  @When("Lena asks for help with her burnt roast chicken without a recipe")
  public void lenaGivesNoRecipe() {
    lenaAsksAboutBurntChickenWithoutRecipe("COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her catastrophe request is sent without a recipe")
  public void requestIsSentWithoutARecipe() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("type").asText()).isEqualTo("STEPS_TO_MITIGATE_CATASTROPHE");
    assertThat(created.has("recipe")).as("recipe in %s", created).isFalse();
  }

  @Given("Lena asked for help with her burnt roast chicken")
  public void lenaAskedAboutBurntChicken() {
    lenaAsksAboutBurntChicken("COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("the rice burns too and Lena asks for help with it")
  public void lenaAsksAboutTheRice() {
    lenaAsksWithoutRecipe("My rice is burnt", "The rice burnt in the pot", "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("she has two catastrophe requests, one about the roast chicken and one about the rice")
  public void twoCatastropheRequests() {
    assertThat(lenasRequests).hasSize(2).doesNotHaveDuplicates();
    assertThat(readRequest(lenasRequests.get(0)).get("title").asText())
        .isEqualTo("My roast chicken is burnt");
    assertThat(readRequest(lenasRequests.get(1)).get("title").asText())
        .isEqualTo("My rice is burnt");
  }

  // R3

  @When("Lena asks for help with her burnt roast chicken and adds a photo of the burnt bottom")
  public void lenaAddsAPhoto() {
    lenaAsksWithHerRecipe(BURNT_ON_THE_BOTTOM, List.of(PHOTO_OF_THE_BURNT_BOTTOM), "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the providers see the photo")
  public void providersSeeThePhoto() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(texts(created.path("pictures"))).containsExactly(PHOTO_OF_THE_BURNT_BOTTOM);
    assertThat(texts(readRequest(lenasRequest()).path("pictures")))
        .containsExactly(PHOTO_OF_THE_BURNT_BOTTOM);
  }

  // R4

  @When("on Saturday at 18:40 Lena asks the Community about her burnt roast chicken")
  public void lenaAsksTheCommunityAt1840() {
    clock.moveTo(saturday("18:40"));
    lenaAsksAboutBurntChicken("COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then(
      "the request announced at 18:40 names the Community among the asked providers, for its"
          + " members to be notified")
  public void announcedRequestNamesTheCommunity() {
    // INV-21: notifying the members is the Notification context's; this context names the
    // Community among the asked providers (step 3 r20)
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(
            OffsetDateTime.parse(created.get("occurredAt").asText())
                .toInstant()
                .truncatedTo(ChronoUnit.MINUTES))
        .isEqualTo(saturday("18:40"));
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .contains("COMMUNITY");
  }

  // R5

  @Given("Lena asked Grandma Avatar about her burnt roast chicken at 18:40")
  public void lenaAskedGrandmaAt1840() {
    lenaAsksGrandmaAt1840();
  }

  /** What Grandma Avatar tells Lena to do (R5.E1, R6). */
  static final String CUT_OFF_THE_BURNT_PART = "cut off the burnt part, make a fresh sauce";

  /** Grandma's mitigation arrives on her exchange, shaped like the spec's example. */
  @When("Grandma Avatar answers {string}")
  public void grandmaAnswers(String explanation) {
    grandmaMitigates(explanation, "8b1d4f5e-0a6c-4d2b-a9f4-3c5a7b9d1e46");
  }

  @Given("Grandma Avatar answered")
  public void grandmaAnswered() {
    grandmaMitigates(CUT_OFF_THE_BURNT_PART, "8b1d4f5e-0a6c-4d2b-a9f4-3c5a7b9d1e47");
  }

  private void grandmaMitigates(String explanation, String messageId) {
    driver.grandmaPublishes(
        """
        {"helpRequest":"%s","answerTitle":"Save the chicken",
         "answer":{"answerType":"STEPS_TO_MITIGATE_CATASTROPHE","recipe":"%s",
                   "explanation":"%s"}}"""
            .formatted(lenasRequest(), HelpRequests.ROAST_CHICKEN.value(), explanation),
        messageId);
    grandmasHelp = driver.nextMessage(providedQueue, "HelpProvided").get("helpId").asText();
  }

  @When("Lena accepts Grandma's answer at 18:44")
  public void lenaAcceptsGrandmasAnswerAt1844() {
    clock.moveTo(saturday("18:44"));
    lenaAccepts(grandmasHelp);
  }

  private void lenaAccepts(String helpId) {
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps/" + helpId + "/acceptance",
            HelpRequests.LENA,
            "cook",
            null);
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Then("her catastrophe request is closed")
  public void herCatastropheRequestIsClosed() {
    assertClosedByAccepting(grandmasHelp, "GRANDMA_AVATAR");
  }

  /**
   * Accepting one help closes Lena's request, and HelpAccepted names that help and its provider.
   */
  private void assertClosedByAccepting(String helpId, String providerType) {
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("CLOSED");
    JsonNode accepted = driver.nextMessage(acceptedQueue, "HelpAccepted");
    assertThat(accepted.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(accepted.get("helpId").asText()).isEqualTo(helpId);
    assertThat(accepted.get("helpProviderType").asText()).isEqualTo(providerType);
    assertThat(accepted.get("helpRequestStatus").asText()).isEqualTo("CLOSED");
  }

  @Given("Lena asked the Community and Grandma Avatar about her burnt roast chicken at 18:40")
  public void lenaAskedBothAt1840() {
    clock.moveTo(saturday("18:40"));
    lenaAsksAboutBurntChicken("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  /** Jonas's mitigation, the spec's catastropheMitigation example without its image. */
  private ResponseEntity<String> jonasMitigates() {
    return driver.post(
        "/help-requests/" + lenasRequest() + "/helps",
        HelpRequests.JONAS,
        "cook,community",
        """
        {"answerTitle":"Save the chicken","helpProviderType":"COMMUNITY",
         "answer":{"answerType":"STEPS_TO_MITIGATE_CATASTROPHE","recipe":"%s",
                   "explanation":"cut off the burnt part, make a fresh sauce"}}"""
            .formatted(HelpRequests.ROAST_CHICKEN.value()));
  }

  @Given("Jonas answered from the Community")
  public void jonasAnsweredFromTheCommunity() {
    ResponseEntity<String> answered = jonasMitigates();
    assertThat(answered.getStatusCode().value()).isEqualTo(201);
    String link = driver.json(answered).get("helpLink").asText();
    jonassHelp = link.substring(link.lastIndexOf('/') + 1);
  }

  @When("Lena accepts Jonas's answer at 18:45")
  public void lenaAcceptsJonassAnswerAt1845() {
    clock.moveTo(saturday("18:45"));
    lenaAccepts(jonassHelp);
  }

  @Then("her catastrophe request is closed, for Grandma Avatar too")
  public void closedForGrandmaToo() {
    // the one HelpAccepted closes the whole request for every provider (AsyncAPI helpAccepted);
    // telling Grandma Avatar is the Notification context's
    assertClosedByAccepting(jonassHelp, "COMMUNITY");
    // her side ends with it: past her deadline (18:45) nothing times out, not even her side alone,
    // which would publish nothing (INV-26)
    clock.moveTo(saturday("18:45:05"));
    driver.noMessage(timedOutQueue);
    assertThat(statusOfLenasRequest()).as("after Grandma's deadline").isEqualTo("CLOSED");
    assertThat(stateOfSide("GRANDMA_AVATAR")).as("Grandma's side").isNotEqualTo("TIMED_OUT");
  }

  // R6

  @When("Lena does not accept it at 18:47: {string}")
  public void lenaDoesNotAcceptItAt1847(String note) {
    clock.moveTo(saturday("18:47"));
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps/" + grandmasHelp + "/rejection",
            HelpRequests.LENA,
            "cook",
            "{\"note\":\"%s\"}".formatted(note));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Then("the request is sent again to Grandma Avatar with the note, deadline 18:52")
  public void sentAgainToGrandmaWithTheNote() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(rejected.path("note").asText()).isEqualTo("still tastes smoky");
    assertThat(rejected.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("GRANDMA_AVATAR");
    assertDeadline(
        rejected.get("askedProviders").get(0).get("deadline"), saturday("18:52"), "Grandma");
    JsonNode grandmasSide = readRequest(lenasRequest()).get("askedProviders").get(0);
    assertThat(grandmasSide.get("state").asText()).isEqualTo("ASKED");
    assertDeadline(grandmasSide.get("deadline"), saturday("18:52"), "Grandma");
  }

  @Then("the Community has until 19:02 and Grandma Avatar until 18:52")
  public void eachSideHasItsNewDeadline() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertNewDeadlines(rejected.get("askedProviders"));
    assertNewDeadlines(readRequest(lenasRequest()).get("askedProviders"));
  }

  private void assertNewDeadlines(JsonNode askedProviders) {
    assertThat(askedProviders.findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertDeadline(
        side(askedProviders, "COMMUNITY").get("deadline"), saturday("19:02"), "Community");
    assertDeadline(
        side(askedProviders, "GRANDMA_AVATAR").get("deadline"), saturday("18:52"), "Grandma");
  }

  private static JsonNode side(JsonNode askedProviders, String providerType) {
    for (JsonNode provider : askedProviders) {
      if (provider.get("providerType").asText().equals(providerType)) {
        return provider;
      }
    }
    throw new AssertionError("no " + providerType + " among " + askedProviders);
  }

  // R7 — notes are shown on future catastrophe requests with the same recipe

  @Given("Lena's earlier catastrophe request with the recipe {string} carries the note {string}")
  public void lenasEarlierRequestCarriesTheNote(String recipeName, String note) {
    assertThat(recipe(recipeName)).isEqualTo(HelpRequests.ROAST_CHICKEN);
    lenaAsksGrandmaAt1840();
    grandmaMitigates(CUT_OFF_THE_BURNT_PART, "8b1d4f5e-0a6c-4d2b-a9f4-3c5a7b9d1e48");
    lenaDoesNotAcceptItAt1847(note);
  }

  @When(
      "next month Lena asks for help with her burnt roast chicken again, with the recipe {string}")
  public void nextMonthLenaAsksAgain(String recipeName) {
    assertThat(recipe(recipeName)).isEqualTo(HelpRequests.ROAST_CHICKEN);
    clock.moveTo(saturday("18:40").plus(Duration.ofDays(28)));
    lenaAsksAboutBurntChicken("COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her new request shows the note {string}")
  public void herNewRequestShowsTheNote(String note) {
    String earlier = lenasRequests.get(lenasRequests.size() - 2);
    assertShowsThePreviousNote(readRequest(lenasRequest()), note, earlier);
  }

  // R8 — cancelling at any time, with an optional note

  @When("Lena cancels at 18:50: {string}")
  public void lenaCancelsAt1850(String note) {
    clock.moveTo(saturday("18:50"));
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/cancellation",
            HelpRequests.LENA,
            "cook",
            "{\"note\":\"%s\"}".formatted(note));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Then("her catastrophe request is cancelled")
  public void herCatastropheRequestIsCancelled() throws InterruptedException {
    assertThat(eventually(this::statusOfLenasRequest, "CANCELLED"))
        .as("status of Lena's request")
        .isEqualTo("CANCELLED");
  }

  /**
   * Reads again every 100 ms until the expected value or 10 s passed: time-outs run in the
   * background.
   */
  private static String eventually(Supplier<String> read, String expected)
      throws InterruptedException {
    Instant giveUp = Instant.now().plusSeconds(10);
    String value = read.get();
    while (!value.equals(expected) && Instant.now().isBefore(giveUp)) {
      Thread.sleep(100);
      value = read.get();
    }
    return value;
  }

  private String statusOfLenasRequest() {
    return readRequest(lenasRequest()).get("status").asText();
  }

  private String stateOfSide(String providerType) {
    return side(readRequest(lenasRequest()).get("askedProviders"), providerType)
        .get("state")
        .asText();
  }

  @Then("the providers see the note {string}")
  public void theProvidersSeeTheNote(String note) {
    JsonNode cancelled = driver.nextMessage(cancelledQueue, "HelpRequestCancelled");
    assertThat(cancelled.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(cancelled.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(cancelled.path("note").asText()).isEqualTo(note);
    assertThat(readRequest(lenasRequest()).path("notes").findValuesAsText("text"))
        .containsExactly(note);
  }

  // R9 — unanswered: cancelled per side after 15 min (Community) or 5 min (Grandma)

  @Given("Lena asked only the Community about her burnt roast chicken at 18:40")
  public void lenaAskedOnlyTheCommunityAt1840() {
    lenaAsksTheCommunityAt1840();
  }

  /** A few seconds after the Community's deadline: the clock ticked while Lena asked. */
  @When("nobody answers until 18:55")
  public void nobodyAnswersUntil1855() {
    clock.moveTo(saturday("18:55:05"));
  }

  @Given("Lena asked only Grandma Avatar about her burnt roast chicken at 18:40")
  public void lenaAskedOnlyGrandmaAt1840() {
    lenaAsksGrandmaAt1840();
  }

  /** A few seconds after Grandma's deadline: the clock ticked while Lena asked. */
  @When("nobody answers until 18:45")
  public void nobodyAnswersUntil1845() {
    clock.moveTo(saturday("18:45:05"));
  }

  @Then("Grandma Avatar is suggested")
  public void grandmaIsSuggested() {
    assertTimedOutAndSuggested("COMMUNITY", "GRANDMA_AVATAR");
  }

  @Then("the Community is suggested")
  public void theCommunityIsSuggested() {
    assertTimedOutAndSuggested("GRANDMA_AVATAR", "COMMUNITY");
  }

  private void assertTimedOutAndSuggested(String timedOutProvider, String suggestedProvider) {
    JsonNode timedOut = driver.nextMessage(timedOutQueue, "HelpRequestTimedOut");
    assertThat(timedOut.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(timedOut.get("timedOutProviders").toString())
        .isEqualTo("[\"" + timedOutProvider + "\"]");
    assertThat(timedOut.get("suggestedProviders").toString())
        .isEqualTo("[\"" + suggestedProvider + "\"]");
    assertThat(readRequest(lenasRequest()).path("suggestedProviders").toString())
        .isEqualTo("[\"" + suggestedProvider + "\"]");
  }

  /** A few seconds after Grandma's deadline: the clock ticked while Lena asked. */
  @When("Grandma Avatar has not answered by 18:45")
  public void grandmaHasNotAnsweredBy1845() {
    clock.moveTo(saturday("18:45:05"));
  }

  /** A side times out alone, with no event of its own (INV-26, step 3 r21). */
  @Then("only Grandma Avatar's side is cancelled")
  public void onlyGrandmasSideIsCancelled() throws InterruptedException {
    assertThat(eventually(() -> stateOfSide("GRANDMA_AVATAR"), "TIMED_OUT"))
        .as("Grandma's side")
        .isEqualTo("TIMED_OUT");
    assertThat(statusOfLenasRequest()).as("status of Lena's request").isEqualTo("OPEN");
    driver.noMessage(timedOutQueue);
  }

  @Then("the Community still has until 18:55")
  public void theCommunityStillHasUntil1855() {
    JsonNode communitysSide = side(readRequest(lenasRequest()).get("askedProviders"), "COMMUNITY");
    assertThat(communitysSide.get("state").asText()).isEqualTo("ASKED");
    assertDeadline(communitysSide.get("deadline"), saturday("18:55"), "Community");
  }

  /** The time-out message is how Lena is told: nobody is left to suggest (INV-26). */
  @Then("Lena is told")
  public void lenaIsTold() {
    JsonNode timedOut = driver.nextMessage(timedOutQueue, "HelpRequestTimedOut");
    assertThat(timedOut.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(timedOut.get("helpRequester").asText())
        .isEqualTo(HelpRequests.LENA.value().toString());
    assertThat(texts(timedOut.get("timedOutProviders")))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(timedOut.get("suggestedProviders").toString()).isEqualTo("[]");
    assertThat(readRequest(lenasRequest()).has("suggestedProviders"))
        .as("suggestedProviders on the request")
        .isFalse();
  }

  @Given("Lena asked the Community about her burnt roast chicken at 18:40")
  public void lenaAskedTheCommunityAt1840() {
    lenaAsksTheCommunityAt1840();
  }

  @When("Jonas answers at 18:54")
  public void jonasAnswersAt1854() {
    clock.moveTo(saturday("18:54"));
    lastResponse = jonasMitigates();
  }

  /** One minute before the Community's deadline: his answer stops the time-out (INV-26). */
  @Then("Jonas's answer counts")
  public void jonassAnswerCounts() {
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
    JsonNode provided = driver.nextMessage(providedQueue, "HelpProvided");
    assertThat(provided.get("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
    assertThat(statusOfLenasRequest()).isEqualTo("ANSWERED");
    clock.moveTo(saturday("18:55:05"));
    driver.noMessage(timedOutQueue);
    assertThat(statusOfLenasRequest()).as("after the deadline").isEqualTo("ANSWERED");
  }
}
