package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;

/** Steps of US-CA-02 in the domain language, through the driver. */
public class IngredientSubstitutionSteps {

  static final UUID PANCAKES = UUID.fromString("2b3c4d5e-6f70-4812-9a3b-4c5d6e7f8091");
  static final UUID BUTTERMILK = UUID.fromString("3c4d5e6f-7081-4923-8b4c-5d6e7f8091a2");
  static final UUID CARBONARA = UUID.fromString("4d5e6f70-8192-4a34-9c5d-6e7f8091a2b3");
  static final UUID BACON = UUID.fromString("5e6f7081-92a3-4b45-8d6e-7f8091a2b3c4");
  static final UUID RISOTTO = UUID.fromString("6f708192-a3b4-4c56-9e7f-8091a2b3c4d5");
  static final UUID SAFFRON = UUID.fromString("708192a3-b4c5-4d67-8f80-91a2b3c4d5e6");
  static final UUID MUSHROOM_SAUCE = UUID.fromString("8192a3b4-c5d6-4e78-9a91-a2b3c4d5e6f7");
  static final UUID CREME_FRAICHE_IN_MUSHROOM_SAUCE =
      UUID.fromString("92a3b4c5-d6e7-4f89-8ba2-b3c4d5e6f708");

  /** What Lena needs in place of the crème fraîche, unless a scenario names it (R2.E1). */
  static final String LENAS_REQUIREMENTS = "200 g, not available";

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

  /** The Help each provider gave on Lena's request, by name (Grandma, Jonas, Mia). */
  private final Map<String, String> helpsBy = new HashMap<>();

  private String acceptedHelp;
  private String acceptedProviderType;

  /** Lena's requests in the order she made them; the last one is "her ingredient request". */
  private final List<String> lenasRequests = new ArrayList<>();

  private ResponseEntity<String> lastResponse;

  public IngredientSubstitutionSteps(
      AmqpAdmin admin, RabbitTemplate rabbit, Environment environment, AcceptanceClock clock) {
    this.admin = admin;
    this.rabbit = rabbit;
    this.environment = environment;
    this.clock = clock;
  }

  /** The story's Monday, 9 November 2026, at the given time in Berlin (CET). */
  static Instant monday(String time) {
    return LocalDateTime.parse("2026-11-09T" + time).atZone(ZoneId.of("Europe/Berlin")).toInstant();
  }

  /** The story's Tuesday, 10 November 2026, at the given time in Berlin (CET). */
  static Instant tuesday(String time) {
    return monday(time).plus(Duration.ofDays(1));
  }

  private static void assertCloseTo(JsonNode deadline, Instant expected, String whose) {
    assertThat(Instant.parse(deadline.asText()))
        .as("%s's deadline", whose)
        .isCloseTo(expected, within(5, ChronoUnit.SECONDS));
  }

  /**
   * After the common reset of every scenario (empty tables, queues, the clock): the story starts on
   * Monday at 09:00, before the first time an example names (R4.E1, Monday 11:00).
   */
  @Before(value = "@US-CA-02", order = 20_000)
  public void mondayMorning() {
    clock.moveTo(monday("09:00"));
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

  /** Lena sends a request; a created one becomes "her ingredient request". */
  private void lenaAsks(String request) {
    lastResponse = driver.post("/help-requests", HelpRequests.LENA, "cook", request);
    if (lastResponse.getStatusCode().value() == 201) {
      lenasRequests.add(driver.json(lastResponse).get("helpRequestId").asText());
    }
  }

  /** Lena asks the given providers for a substitute for the crème fraîche in her tomato soup. */
  private void lenaAsksForCremeFraiche(String requirements, String... providerTypes) {
    lenaAsks(
        """
        {"title":"Crème fraîche in tomato soup","type":"INGREDIENT_SUBSTITUTE",
         "description":"The shop has none","recipe":"%s","ingredient":"%s",
         "ingredientName":"crème fraîche","substituteRequirements":"%s","preferredProvider":%s}"""
            .formatted(
                HelpRequests.TOMATO_SOUP.value(),
                HelpRequests.CREME_FRAICHE.value(),
                requirements,
                Arrays.stream(providerTypes)
                    .map(type -> "\"" + type + "\"")
                    .collect(Collectors.joining(",", "[", "]"))));
  }

  // R1

  @When("Lena asks Grandma Avatar for a substitute for the crème fraîche in her tomato soup")
  public void lenaAsksGrandma() {
    lenaAsksForCremeFraiche(LENAS_REQUIREMENTS, "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("Grandma Avatar receives the request about the crème fraîche")
  public void grandmaReceivesTheIngredientRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("GRANDMA_AVATAR");
    assertThat(created.get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
    assertThat(created.get("ingredientName").asText()).isEqualTo("crème fraîche");
  }

  @When("Lena asks the Community and Grandma Avatar for a substitute for the crème fraîche")
  public void lenaAsksBoth() {
    lenaAsksForCremeFraiche(LENAS_REQUIREMENTS, "COMMUNITY", "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the Community and Grandma Avatar receive the same ingredient request")
  public void bothReceiveTheSameIngredientRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(created.get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
  }

  @When("Tom, on the free plan, asks the Community for a substitute for an ingredient")
  public void tomAsksTheCommunity() {
    lastResponse =
        driver.post(
            "/help-requests",
            HelpRequestSteps.TOM,
            "cook",
            """
            {"title":"Buttermilk in pancakes","type":"INGREDIENT_SUBSTITUTE",
             "description":"I have none","recipe":"%s","ingredient":"%s",
             "ingredientName":"buttermilk","substituteRequirements":"250 ml",
             "preferredProvider":["COMMUNITY"]}"""
                .formatted(PANCAKES, BUTTERMILK));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the ingredient request is sent to the Community")
  public void ingredientRequestIsSentToTheCommunity() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("requester").asText())
        .isEqualTo(HelpRequestSteps.TOM.value().toString());
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("COMMUNITY");
  }

  @When("Lena asks chef Marco for a substitute for the crème fraîche")
  public void lenaAsksChefMarco() {
    lenaAsksForCremeFraiche(LENAS_REQUIREMENTS, "CHEF");
  }

  @Then("the ingredient request is refused")
  public void ingredientRequestIsRefused() {
    // the schema's else branch: only a menu request may name a chef → 400 (step 9, US-CA-02)
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(400);
    assertThat(lastResponse.getBody()).contains("\"code\":\"VALIDATION_FAILED\"");
  }

  @Then("no ingredient request is sent")
  public void noIngredientRequestIsSent() {
    driver.noMessage(createdQueue);
  }

  // R2

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

  @When("Lena asks for a substitute for {string} crème fraîche in her tomato soup")
  public void lenaAsksWithRequirements(String requirements) {
    lenaAsksForCremeFraiche(requirements, "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her ingredient request shows the tomato soup, the crème fraîche and {string}")
  public void herRequestShowsTheIngredient(String requirements) {
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.get("recipe").asText())
        .isEqualTo(HelpRequests.TOMATO_SOUP.value().toString());
    assertThat(request.get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
    assertThat(request.get("ingredientName").asText()).isEqualTo("crème fraîche");
    assertThat(request.get("substituteRequirements").asText()).isEqualTo(requirements);
  }

  /** Lena asks the Community for a substitute for the bacon in her carbonara. */
  private void lenaAsksForBacon(String requirements) {
    lenaAsks(
        """
        {"title":"Bacon in carbonara","type":"INGREDIENT_SUBSTITUTE",
         "description":"A guest is vegetarian","recipe":"%s","ingredient":"%s",
         "ingredientName":"bacon","substituteRequirements":"%s",
         "preferredProvider":["COMMUNITY"]}"""
            .formatted(CARBONARA, BACON, requirements));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("Lena asks for a substitute for the bacon in her carbonara: {string}")
  public void lenaAsksForTheBacon(String requirements) {
    lenaAsksForBacon(requirements);
  }

  @Then("her ingredient request shows {string}")
  public void herRequestShowsTheRequirements(String requirements) {
    assertThat(readRequest(lenasRequest()).get("substituteRequirements").asText())
        .isEqualTo(requirements);
  }

  @When("Lena asks for a substitute for the crème fraîche and for the bacon")
  public void lenaAsksForTwoIngredients() {
    lenaAsksForCremeFraiche(LENAS_REQUIREMENTS, "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
    lenaAsksForBacon("vegetarian guest");
  }

  @Then("she has two ingredient requests, one about the crème fraîche and one about the bacon")
  public void sheHasTwoIngredientRequests() {
    assertThat(lenasRequests).hasSize(2);
    assertThat(lenasRequests.stream().map(id -> readRequest(id).get("ingredientName").asText()))
        .containsExactly("crème fraîche", "bacon");
  }

  // R3 — substitutes, mixes and accepting one

  /** One substitute for the crème fraîche: one ingredient, or several forming a mix. */
  private static String substitute(String... gramsOfIngredients) {
    return substituteFor(HelpRequests.CREME_FRAICHE.value(), gramsOfIngredients);
  }

  private static String substituteFor(UUID ingredient, String... gramsOfIngredients) {
    return Arrays.stream(gramsOfIngredients)
        .map(
            gramsOfIngredient -> {
              String[] gramsAndName = gramsOfIngredient.split(" g ", 2);
              return "{\"name\":\"%s\",\"value\":%s,\"unit\":\"GRAM\"}"
                  .formatted(gramsAndName[1], gramsAndName[0]);
            })
        .collect(
            Collectors.joining(
                ",",
                "{\"ingredient\":\"%s\",\"substituteIngredients\":[".formatted(ingredient),
                "]}"));
  }

  private static String substitutesAnswer(String... substitutes) {
    return substitutesAnswerFor(HelpRequests.TOMATO_SOUP.value(), substitutes);
  }

  private static String substitutesAnswerFor(UUID recipe, String... substitutes) {
    return "{\"answerType\":\"INGREDIENT_SUBSTITUTE\",\"recipe\":\"%s\",\"substitute\":[%s]}"
        .formatted(recipe, String.join(",", substitutes));
  }

  /** Grandma's answer arrives on her exchange; its Help is "Grandma's answer". */
  private void grandmaProposes(String messageId, String... substitutes) {
    driver.grandmaPublishes(
        "{\"helpRequest\":\"%s\",\"answerTitle\":\"Grandma's ideas\",\"answer\":%s}"
            .formatted(lenasRequest(), substitutesAnswer(substitutes)),
        messageId);
    helpsBy.put(
        "Grandma", driver.nextMessage(providedQueue, "HelpProvided").get("helpId").asText());
  }

  /** A Community member answers Lena's request; the Help id comes from the returned link. */
  private ResponseEntity<String> communityProposes(CookId member, String... substitutes) {
    return driver.post(
        "/help-requests/" + lenasRequest() + "/helps",
        member,
        "cook,community",
        "{\"answerTitle\":\"Try this\",\"helpProviderType\":\"COMMUNITY\",\"answer\":%s}"
            .formatted(substitutesAnswer(substitutes)));
  }

  private String helpIdOf(ResponseEntity<String> created) {
    String link = driver.json(created).get("helpLink").asText();
    return link.substring(link.lastIndexOf('/') + 1);
  }

  private void lenaTakes(String provider, String providerType) {
    acceptedHelp = helpsBy.get(provider);
    acceptedProviderType = providerType;
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps/" + acceptedHelp + "/acceptance",
            HelpRequests.LENA,
            "cook",
            null);
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Given("Lena asked Grandma Avatar for a substitute for the crème fraîche")
  public void lenaAskedGrandma() {
    lenaAsksGrandma();
  }

  @Given("Lena asked the Community for a substitute for the crème fraîche")
  public void lenaAskedTheCommunity() {
    lenaAsksForCremeFraiche(LENAS_REQUIREMENTS, "COMMUNITY");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Given("Lena asked the Community and Grandma Avatar for a substitute for the crème fraîche")
  public void lenaAskedBoth() {
    lenaAsksBoth();
  }

  @When("Grandma Avatar proposes yogurt, quark and sour cream")
  public void grandmaProposesThree() {
    grandmaProposes(
        "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c01",
        substitute("200 g yogurt"),
        substitute("200 g quark"),
        substitute("200 g sour cream"));
  }

  @When("Lena takes Grandma's yogurt")
  public void lenaTakesGrandmasYogurt() {
    lenaTakes("Grandma", "GRANDMA_AVATAR");
  }

  @Then("her ingredient request is closed")
  public void herIngredientRequestIsClosed() {
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("CLOSED");
    JsonNode accepted = driver.nextMessage(acceptedQueue, "HelpAccepted");
    assertThat(accepted.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(accepted.get("helpId").asText()).isEqualTo(acceptedHelp);
    assertThat(accepted.get("helpProviderType").asText()).isEqualTo(acceptedProviderType);
    assertThat(accepted.get("helpRequestStatus").asText()).isEqualTo("CLOSED");
  }

  @When("Jonas proposes yogurt and Mia proposes mascarpone")
  public void jonasAndMiaPropose() {
    ResponseEntity<String> jonas =
        communityProposes(HelpRequests.JONAS, substitute("200 g yogurt"));
    assertThat(jonas.getStatusCode().value()).isEqualTo(201);
    helpsBy.put("Jonas", helpIdOf(jonas));
    ResponseEntity<String> mia =
        communityProposes(HelpRequestSteps.MIA, substitute("200 g mascarpone"));
    assertThat(mia.getStatusCode().value()).isEqualTo(201);
    helpsBy.put("Mia", helpIdOf(mia));
  }

  @When("Lena takes Jonas's yogurt")
  public void lenaTakesJonassYogurt() {
    lenaTakes("Jonas", "COMMUNITY");
  }

  @When("Grandma Avatar proposes {string} or 200 g quark")
  public void grandmaProposesAMixOrQuark(String mix) {
    grandmaProposes(
        "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c03",
        substitute(mix.split(" \\+ ")),
        substitute("200 g quark"));
  }

  @When("Lena takes Grandma's mix")
  public void lenaTakesGrandmasMix() {
    lenaTakes("Grandma", "GRANDMA_AVATAR");
  }

  @Then(
      "her ingredient request is closed with the mix of 100 g mascarpone and 100 g yogurt accepted")
  public void closedWithTheMixAccepted() {
    herIngredientRequestIsClosed();
    ResponseEntity<String> read =
        driver.get(
            "/help-requests/" + lenasRequest() + "/helps/" + acceptedHelp,
            HelpRequests.LENA,
            "cook");
    JsonNode help = driver.json(read);
    assertThat(help.get("isAccepted").asBoolean()).isTrue();
    JsonNode mix = help.path("answer").path("substitute").get(0).path("substituteIngredients");
    assertThat(mix.findValuesAsText("name")).containsExactly("mascarpone", "yogurt");
    // Grandma's amounts arrive as JSON numbers (doubles): compared as amounts, not as text
    assertThat(mix.get(0).path("value").decimalValue()).isEqualByComparingTo("100");
    assertThat(mix.get(1).path("value").decimalValue()).isEqualByComparingTo("100");
  }

  @Given("Lena took Grandma's mix for the crème fraîche")
  public void lenaTookGrandmasMix() {
    lenaAskedGrandma();
    grandmaProposesAMixOrQuark("100 g mascarpone + 100 g yogurt");
    lenaTakesGrandmasMix();
    driver.nextMessage(acceptedQueue, "HelpAccepted");
  }

  @When("she cooks with mascarpone only")
  public void sheCooksWithMascarponeOnly() {
    // what she cooks is her choice: the service is not told
  }

  @Then("her ingredient request stays closed")
  public void herIngredientRequestStaysClosed() {
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("CLOSED");
    driver.noMessage(acceptedQueue);
  }

  @When("Grandma Avatar proposes quark")
  public void grandmaProposesQuark() {
    grandmaProposes("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c05", substitute("200 g quark"));
  }

  @When("Lena takes Grandma's quark")
  public void lenaTakesGrandmasQuark() {
    lenaTakes("Grandma", "GRANDMA_AVATAR");
  }

  @Then("her ingredient request is closed for the Community too")
  public void closedForTheCommunityToo() {
    herIngredientRequestIsClosed();
    ResponseEntity<String> jonasLate =
        communityProposes(HelpRequests.JONAS, substitute("200 g yogurt"));
    assertThat(jonasLate.getStatusCode().value()).as("Jonas answers afterwards").isEqualTo(409);
    assertThat(jonasLate.getBody()).contains("\"code\":\"INVALID_STATE\"");
  }

  // R4 — not accepted: sent again to the same providers, new 24 h deadline

  private Instant rejectedAt;

  private void lenaRejects(String provider, String note) {
    rejectedAt = clock.instant();
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps/" + helpsBy.get(provider) + "/rejection",
            HelpRequests.LENA,
            "cook",
            "{\"note\":\"%s\"}".formatted(note));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @Given("Grandma Avatar proposed sour cream")
  public void grandmaProposedSourCream() {
    grandmaProposes("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c11", substitute("200 g sour cream"));
  }

  @When("on Monday at 11:00 Lena rejects the sour cream: {string}")
  public void onMondayAt1100LenaRejects(String note) {
    clock.moveTo(monday("11:00"));
    lenaRejects("Grandma", note);
  }

  @Then("the ingredient request is sent again to Grandma with the deadline Tuesday 11:00")
  public void sentAgainToGrandmaWithTheNewDeadline() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(rejected.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("GRANDMA_AVATAR");
    assertCloseTo(
        rejected.get("askedProviders").get(0).get("deadline"), tuesday("11:00"), "Grandma");
    JsonNode grandmasSide = readRequest(lenasRequest()).get("askedProviders").get(0);
    assertThat(grandmasSide.get("state").asText()).isEqualTo("ASKED");
    assertCloseTo(grandmasSide.get("deadline"), tuesday("11:00"), "Grandma");
  }

  @Given("Lena asked the Community for a substitute for the saffron in her risotto")
  public void lenaAskedForSaffron() {
    lenaAsks(
        """
        {"title":"Saffron in risotto","type":"INGREDIENT_SUBSTITUTE",
         "description":"Saffron is too dear","recipe":"%s","ingredient":"%s",
         "ingredientName":"saffron","substituteRequirements":"for 4 servings",
         "preferredProvider":["COMMUNITY"]}"""
            .formatted(RISOTTO, SAFFRON));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("Jonas answers that there is no good substitute for saffron and proposes 1 g turmeric")
  public void jonasFindsNoGoodSubstitute() {
    // an answer always proposes a substitute (INV-12, step 3 r17): Jonas names the closest he knows
    ResponseEntity<String> jonas =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps",
            HelpRequests.JONAS,
            "cook,community",
            "{\"answerTitle\":\"No good substitute for saffron\",\"helpProviderType\":\"COMMUNITY\","
                + "\"answer\":%s}"
                    .formatted(
                        substitutesAnswerFor(RISOTTO, substituteFor(SAFFRON, "1 g turmeric"))));
    assertThat(jonas.getStatusCode().value()).isEqualTo(201);
    helpsBy.put("Jonas", helpIdOf(jonas));
  }

  @When("Lena rejects Jonas's answer")
  public void lenaRejectsJonassAnswer() {
    lenaRejects("Jonas", "no good substitute");
  }

  @Then("her ingredient request stays open")
  public void herIngredientRequestStaysOpen() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("helpRequestStatus").asText()).isEqualTo("OPEN");
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("OPEN");
  }

  @When("Lena rejects the sour cream: {string}")
  public void lenaRejectsTheSourCream(String note) {
    lenaRejects("Grandma", note);
  }

  @Then("the ingredient request is sent again to both")
  public void sentAgainToBoth() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    for (JsonNode asked : rejected.get("askedProviders")) {
      assertCloseTo(
          asked.get("deadline"),
          rejectedAt.plus(Duration.ofHours(24)),
          asked.get("providerType").asText());
    }
    assertThat(readRequest(lenasRequest()).get("askedProviders").findValuesAsText("state"))
        .containsExactly("ASKED", "ASKED");
  }

  // R5 — rejected substitutes are kept as notes and not proposed again, now or later

  private JsonNode communitysView;

  @Given("Grandma Avatar proposed mascarpone")
  public void grandmaProposedMascarpone() {
    grandmaProposes("a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c21", substitute("200 g mascarpone"));
  }

  @When("Lena rejects the mascarpone: {string}")
  public void lenaRejectsTheMascarpone(String note) {
    lenaRejects("Grandma", note);
  }

  @Then("her ingredient request keeps the note {string} on Grandma's answer")
  public void keepsTheNoteOnGrandmasAnswer(String note) {
    JsonNode notes = readRequest(lenasRequest()).get("notes");
    assertThat(notes).as("notes").isNotNull().hasSize(1);
    assertThat(notes.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(notes.get(0).get("text").asText()).isEqualTo(note);
    ResponseEntity<String> grandmasHelp =
        driver.get(
            "/help-requests/" + lenasRequest() + "/helps/" + helpsBy.get("Grandma"),
            HelpRequests.LENA,
            "cook");
    assertThat(driver.json(grandmasHelp).get("rejectionNote").asText()).isEqualTo(note);
  }

  @Given("Lena rejected Grandma's mascarpone: {string}")
  public void lenaRejectedGrandmasMascarpone(String note) {
    grandmaProposedMascarpone();
    lenaRejects("Grandma", note);
    driver.nextMessage(rejectedQueue, "HelpRejected");
  }

  @When("the Community reads her ingredient request")
  public void theCommunityReadsHerRequest() {
    communitysView = readRequest(lenasRequest());
  }

  @Then("the Community sees the note {string}")
  public void theCommunitySeesTheNote(String note) {
    assertThat(communitysView.get("notes").findValuesAsText("text")).containsExactly(note);
  }

  @Then("Jonas is refused when he proposes mascarpone")
  public void jonasIsRefusedForMascarpone() {
    ResponseEntity<String> jonas =
        communityProposes(HelpRequests.JONAS, substitute("200 g mascarpone"));
    assertThat(jonas.getStatusCode().value()).isEqualTo(422);
    assertThat(jonas.getBody())
        .contains("\"code\":\"SUBSTITUTE_REJECTED_BEFORE\"")
        .contains("\"rule\":\"INV-15\"");
  }

  @Given("Lena rejected Grandma's mascarpone for the crème fraîche in her tomato soup: {string}")
  public void lenaRejectedMascarponeInHerTomatoSoup(String note) {
    lenaAskedGrandma();
    lenaRejectedGrandmasMascarpone(note);
  }

  @When("next week she asks for a substitute for the crème fraîche in her mushroom sauce")
  public void nextWeekSheAsksAboutHerMushroomSauce() {
    clock.moveTo(monday("09:00").plus(Duration.ofDays(7)));
    lenaAsks(
        """
        {"title":"Crème fraîche in mushroom sauce","type":"INGREDIENT_SUBSTITUTE",
         "description":"Still none in the shop","recipe":"%s","ingredient":"%s",
         "ingredientName":"crème fraîche","substituteRequirements":"150 g",
         "preferredProvider":["COMMUNITY"]}"""
            .formatted(MUSHROOM_SAUCE, CREME_FRAICHE_IN_MUSHROOM_SAUCE));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("her new ingredient request shows the earlier note {string}")
  public void herNewRequestShowsTheEarlierNote(String note) {
    String earlier = lenasRequests.get(lenasRequests.size() - 2);
    JsonNode previous = readRequest(lenasRequest()).get("previousNotes");
    assertThat(previous).as("previousNotes of the new request").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo(note);
    assertThat(previous.get(0).get("helpRequestLink").asText())
        .endsWith("/help-requests/" + earlier);
  }

  /** The earlier rejection is only shown: a later request does not refuse it (INV-15 S4). */
  @Then("Jonas's mascarpone for it is not refused")
  public void jonasMascarponeForTheNewRequestIsNotRefused() {
    ResponseEntity<String> jonas =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps",
            HelpRequests.JONAS,
            "cook,community",
            "{\"answerTitle\":\"Try this\",\"helpProviderType\":\"COMMUNITY\",\"answer\":%s}"
                .formatted(
                    substitutesAnswerFor(
                        MUSHROOM_SAUCE,
                        substituteFor(CREME_FRAICHE_IN_MUSHROOM_SAUCE, "150 g mascarpone"))));
    assertThat(jonas.getStatusCode().value()).isEqualTo(201);
    assertThat(driver.nextMessage(providedQueue, "HelpProvided").get("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
  }

  // R6 — the cook can cancel an open request at any time, with an optional note

  private void lenaCancels(String cancellation) {
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/cancellation",
            HelpRequests.LENA,
            "cook",
            cancellation);
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  @When("she cancels her ingredient request: {string}")
  public void sheCancelsWithANote(String note) {
    lenaCancels("{\"note\":\"%s\"}".formatted(note));
  }

  @Then("her ingredient request is cancelled and the providers see the note {string}")
  public void cancelledAndTheProvidersSeeTheNote(String note) {
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("CANCELLED");
    JsonNode cancelled = driver.nextMessage(cancelledQueue, "HelpRequestCancelled");
    assertThat(cancelled.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(cancelled.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(cancelled.path("note").asText()).isEqualTo(note);
  }

  @When("she cancels her ingredient request without a note")
  public void sheCancelsWithoutANote() {
    lenaCancels(null);
  }

  @Then("her ingredient request is cancelled")
  public void herIngredientRequestIsCancelled() {
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("CANCELLED");
    JsonNode cancelled = driver.nextMessage(cancelledQueue, "HelpRequestCancelled");
    assertThat(cancelled.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(cancelled.has("note")).as("a note in HelpRequestCancelled").isFalse();
  }

  // R7 — unanswered after 24 h: cancelled; a provider not yet asked is suggested

  @Given("Lena asked only the Community for a substitute on Monday at 18:00")
  public void lenaAskedOnlyTheCommunityOnMondayEvening() {
    clock.moveTo(monday("18:00"));
    lenaAskedTheCommunity();
  }

  @Given("Lena asked only Grandma Avatar for a substitute on Monday at 10:00")
  public void lenaAskedOnlyGrandmaOnMondayMorning() {
    clock.moveTo(monday("10:00"));
    lenaAskedGrandma();
  }

  @Given("Lena asked the Community and Grandma Avatar for a substitute on Monday at 18:00")
  public void lenaAskedBothOnMondayEvening() {
    clock.moveTo(monday("18:00"));
    lenaAskedBoth();
  }

  /** Just past the 24 h deadline; the time-out poller of the running service does the rest. */
  @When("nobody answers until Tuesday at {int}:00")
  public void nobodyAnswersUntilTuesday(int hour) {
    clock.moveTo(tuesday("%02d:00:05".formatted(hour)));
  }

  private void assertCancelledAfterTimeOut(String timedOutProvider, String suggestedProvider) {
    JsonNode timedOut = driver.nextMessage(timedOutQueue, "HelpRequestTimedOut");
    assertThat(timedOut.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(timedOut.get("timedOutProviders").toString())
        .isEqualTo("[\"" + timedOutProvider + "\"]");
    assertThat(timedOut.get("suggestedProviders").toString())
        .isEqualTo("[\"" + suggestedProvider + "\"]");
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(request.path("suggestedProviders").toString())
        .isEqualTo("[\"" + suggestedProvider + "\"]");
  }

  @Then("her ingredient request is cancelled and Grandma Avatar is suggested")
  public void cancelledAndGrandmaSuggested() {
    assertCancelledAfterTimeOut("COMMUNITY", "GRANDMA_AVATAR");
  }

  @Then("her ingredient request is cancelled and the Community is suggested")
  public void cancelledAndTheCommunitySuggested() {
    assertCancelledAfterTimeOut("GRANDMA_AVATAR", "COMMUNITY");
  }

  @Then("her ingredient request is cancelled and Lena is told")
  public void cancelledAndLenaIsTold() {
    JsonNode timedOut = driver.nextMessage(timedOutQueue, "HelpRequestTimedOut");
    assertThat(timedOut.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(timedOut.get("helpRequester").asText())
        .isEqualTo(HelpRequests.LENA.value().toString());
    List<String> timedOutProviders = new ArrayList<>();
    timedOut.get("timedOutProviders").forEach(provider -> timedOutProviders.add(provider.asText()));
    assertThat(timedOutProviders).containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(timedOut.get("suggestedProviders").toString()).isEqualTo("[]");
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(request.has("suggestedProviders")).as("suggestedProviders on the request").isFalse();
  }

  @When("Jonas proposes yogurt on Tuesday at 17:59")
  public void jonasProposesJustBeforeTheDeadline() {
    clock.moveTo(tuesday("17:59"));
    lastResponse = communityProposes(HelpRequests.JONAS, substitute("200 g yogurt"));
  }

  @Then("his proposal counts")
  public void hisProposalCounts() {
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
    JsonNode provided = driver.nextMessage(providedQueue, "HelpProvided");
    assertThat(provided.get("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
    assertThat(readRequest(lenasRequest()).get("status").asText()).isEqualTo("ANSWERED");
  }
}
