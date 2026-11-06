package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.But;
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
import java.util.stream.IntStream;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Steps of US-CA-03 in the domain language, through the driver. */
public class HelpRequestSteps {

  static final CookId TOM = new CookId(UUID.fromString("6a7b8c9d-0e1f-4a2b-8c3d-4e5f6a7b8c9d"));
  static final CookId MIA = new CookId(UUID.fromString("4c5d6e7f-8a9b-4c0d-9e1f-2a3b4c5d6e70"));
  static final UUID BREAD = UUID.fromString("9e8d7c6b-5a49-4382-9716-05f4e3d2c1b0");
  static final UUID BREAD_STEP_2 = UUID.fromString("1f2e3d4c-5b6a-4798-8b7a-6c5d4e3f2a1b");
  static final UUID STEP_6 = UUID.fromString("c3d4e5f6-a7b8-4c9d-8e0f-2a3b4c5d6e7f");

  private final AmqpAdmin admin;
  private final RabbitTemplate rabbit;
  private final JdbcClient jdbc;
  private final Environment environment;
  private final AcceptanceClock clock;
  private AcceptanceDriver driver;
  private String createdQueue;
  private String providedQueue;
  private String acceptedQueue;
  private String rejectedQueue;
  private String cancelledQueue;
  private String timedOutQueue;

  /** Lena's requests in the order she made them; the last one is "her request". */
  private final List<String> lenasRequests = new ArrayList<>();

  /** The Help each provider gave on Lena's request, by name (Jonas, Mia, Grandma). */
  private final Map<String, String> helpsBy = new HashMap<>();

  private String acceptedHelp;
  private String acceptedProviderType;
  private ResponseEntity<String> lastResponse;

  public HelpRequestSteps(
      AmqpAdmin admin,
      RabbitTemplate rabbit,
      JdbcClient jdbc,
      Environment environment,
      AcceptanceClock clock) {
    this.admin = admin;
    this.rabbit = rabbit;
    this.jdbc = jdbc;
    this.environment = environment;
    this.clock = clock;
  }

  /** The story's Saturday, 7 November 2026, at the given time in Berlin (CET). */
  static Instant saturday(String time) {
    return friday(time).plus(Duration.ofDays(1));
  }

  /** The story's Friday, 6 November 2026, at the given time in Berlin (CET). */
  static Instant friday(String time) {
    return LocalDateTime.parse("2026-11-06T" + time).atZone(ZoneId.of("Europe/Berlin")).toInstant();
  }

  /** Only the test's own setup touches the database: each scenario starts empty. */
  @Before
  public void emptyState() {
    clock.moveTo(friday("18:00"));
    driver =
        new AcceptanceDriver(
            admin, rabbit, Integer.parseInt(environment.getRequiredProperty("local.server.port")));
    // one statement: it waits for a poller's transaction and empties all tables at once
    jdbc.sql(
            "truncate cooking_assistance.help_request_note, cooking_assistance.help,"
                + " cooking_assistance.asked_provider, cooking_assistance.help_request,"
                + " cooking_assistance.outbox, cooking_assistance.processed_message")
        .update();
    createdQueue = driver.listenTo(AcceptanceDriver.CREATED);
    providedQueue = driver.listenTo(AcceptanceDriver.PROVIDED);
    acceptedQueue = driver.listenTo(AcceptanceDriver.ACCEPTED);
    // every send channel has a queue, so no message of a scenario is unroutable (mandatory)
    rejectedQueue = driver.listenTo(AcceptanceDriver.REJECTED);
    cancelledQueue = driver.listenTo(AcceptanceDriver.CANCELLED);
    timedOutQueue = driver.listenTo(AcceptanceDriver.TIMED_OUT);
    driver.emptyDeadLetters();
  }

  /** Between scenarios nothing is overdue: the time-out poller leaves the last requests alone. */
  @After
  public void backToStoryTime() {
    clock.moveTo(friday("18:00"));
  }

  // Lena's actions on her requests, in the domain language

  /** Lena sends a request; a created one becomes "her request". */
  private void lenaAsks(String request) {
    lastResponse = driver.post("/help-requests", HelpRequests.LENA, "cook", request);
    if (lastResponse.getStatusCode().value() == 201) {
      lenasRequests.add(driver.json(lastResponse).get("helpRequestId").asText());
    }
  }

  /** Lena asks the given providers how to keep step 4 of her Hollandaise from splitting. */
  private void lenaAsksAboutStep4(String... providerTypes) {
    lenaAsks(
        """
        {"title":"How do I keep the sauce from splitting?","type":"PREPARATION_STEP_EXPLANATION",
         "description":"Step 4 of my Hollandaise keeps splitting","recipe":"%s","howToStep":"%s",
         "preferredProvider":%s}"""
            .formatted(
                HelpRequests.HOLLANDAISE.value(),
                HelpRequests.STEP_4.value(),
                Arrays.stream(providerTypes)
                    .map(type -> "\"" + type + "\"")
                    .collect(Collectors.joining(",", "[", "]"))));
  }

  private void lenaAsksTheCommunityAbout(UUID step, String question, String extra) {
    lenaAsks(
        """
        {"title":"%s","type":"PREPARATION_STEP_EXPLANATION",
         "description":"My Hollandaise keeps splitting","recipe":"%s","howToStep":"%s",
         "preferredProvider":["COMMUNITY"]%s}"""
            .formatted(question, HelpRequests.HOLLANDAISE.value(), step, extra));
  }

  private String lenasRequest() {
    return lenasRequests.getLast();
  }

  private void lenaAccepts(String provider, String providerType) {
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

  /** As in the domain examples of R5, Lena decides on Friday at 20:00. */
  private void lenaRejectsOnFridayEvening(String provider, String note) {
    clock.moveTo(friday("20:00"));
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/helps/" + helpsBy.get(provider) + "/rejection",
            HelpRequests.LENA,
            "cook",
            "{\"note\":\"%s\"}".formatted(note));
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  private void lenaCancels(String cancellation) {
    lastResponse =
        driver.post(
            "/help-requests/" + lenasRequest() + "/cancellation",
            HelpRequests.LENA,
            "cook",
            cancellation);
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(200);
  }

  private JsonNode readRequest(String helpRequestId) {
    ResponseEntity<String> read =
        driver.get("/help-requests/" + helpRequestId, HelpRequests.JONAS, "cook,community");
    assertThat(read.getStatusCode().value()).isEqualTo(200);
    return driver.json(read);
  }

  private String statusOfLenasRequest() {
    return readRequest(lenasRequest()).get("status").asText();
  }

  // the providers' answers

  private void grandmaAnswers(UUID step, int photos, String messageId) {
    String images =
        IntStream.rangeClosed(1, photos)
            .mapToObj(i -> "\"https://larder.org/media/images/butter-" + i + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    driver.grandmaPublishes(
        """
        {"helpRequest":"%s","answerTitle":"Butter in a thin stream",
         "answer":{"answerType":"PREPARATION_STEP_EXPLANATION","recipe":"%s","howToStep":"%s",
         "description":"butter in a thin stream","images":%s}}"""
            .formatted(lenasRequest(), HelpRequests.HOLLANDAISE.value(), step, images),
        messageId);
  }

  /** Grandma's answer about step 4 has been announced; its Help is "Grandma's answer". */
  private void grandmaAnsweredStep4(String messageId) {
    grandmaAnswers(HelpRequests.STEP_4.value(), 0, messageId);
    helpsBy.put(
        "Grandma", driver.nextMessage(providedQueue, "HelpProvided").get("helpId").asText());
  }

  private ResponseEntity<String> communityAnswers(CookId member) {
    return driver.post(
        "/help-requests/" + lenasRequest() + "/helps",
        member,
        "cook,community",
        """
        {"answerTitle":"Warm the bowl","helpProviderType":"COMMUNITY",
         "answer":{"answerType":"PREPARATION_STEP_EXPLANATION","recipe":"%s","howToStep":"%s",
         "description":"Add the butter slowly to a warm bowl"}}"""
            .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value()));
  }

  /** The id of the Help a successful answer created, from the helpLink it returned. */
  private String helpIdOf(ResponseEntity<String> created) {
    String link = driver.json(created).get("helpLink").asText();
    return link.substring(link.lastIndexOf('/') + 1);
  }

  // what the checks expect

  /** A new 12 h deadline from Friday 20:00, within the seconds the clock ticked meanwhile. */
  private static void assertDeadlineIsSaturdayAt0800(JsonNode deadline, String whose) {
    assertThat(Instant.parse(deadline.asText()))
        .as("%s's deadline", whose)
        .isCloseTo(saturday("08:00"), within(5, ChronoUnit.SECONDS));
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

  // R1 — who can be asked

  @When("Lena asks Grandma Avatar about step {int} of her Hollandaise")
  public void lenaAsksGrandma(int step) {
    lenaAsksAboutStep4("GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("Grandma Avatar receives the request about step {int}")
  public void grandmaReceivesTheRequest(int step) {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("GRANDMA_AVATAR");
    assertThat(created.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
  }

  @When("Lena asks the Community and Grandma Avatar about step {int} of her Hollandaise")
  public void lenaAsksCommunityAndGrandma(int step) {
    lenaAsksAboutStep4("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @Then("the Community and Grandma Avatar receive the same request")
  public void bothReceiveTheSameRequest() {
    JsonNode created = driver.nextMessage(createdQueue, "HelpRequestCreated");
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
  }

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
    assertThat(created.get("askedProviders").findValuesAsText("providerType"))
        .containsExactly("COMMUNITY");
  }

  @When("Lena asks chef Marco about step {int} of her Hollandaise")
  public void lenaAsksChefMarco(int step) {
    lenaAsksAboutStep4("CHEF");
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

  // R2 — one step per request

  @Given("Lena asked the Community about step {int} of her Hollandaise")
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
    JsonNode request = readRequest(lenasRequest());
    assertThat(request.get("recipe").asText())
        .isEqualTo(HelpRequests.HOLLANDAISE.value().toString());
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
    assertThat(readRequest(lenasRequests.get(1)).get("howToStep").asText())
        .isEqualTo(STEP_6.toString());
  }

  @When("she tries to add step {int} to that request")
  public void sheTriesToAddAStep(int step) {
    lastResponse =
        driver.send(
            HttpMethod.PATCH,
            "/help-requests/" + lenasRequest(),
            HelpRequests.LENA,
            "cook",
            "{\"howToStep\":\"" + STEP_6 + "\"}");
  }

  @Then("nothing is added: the request is still about step {int} only")
  public void nothingIsAdded(int step) {
    // the API offers no operation that changes a request (OpenAPI: no PATCH)
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(405);
    assertThat(readRequest(lenasRequest()).get("howToStep").asText())
        .isEqualTo(HelpRequests.STEP_4.value().toString());
  }

  // R3 — a request is text only

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

  @But("the same request without the photo is accepted")
  public void sameRequestWithoutPhotoIsAccepted() {
    lenaAsksTheCommunityAbout(HelpRequests.STEP_4.value(), "Sauce splits", "");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  // R4 — answers, and accepting one closes the request

  @Given("Lena asked Grandma Avatar about step {int} of her Hollandaise")
  public void lenaAskedGrandma(int step) {
    lenaAsksAboutStep4("GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("Grandma Avatar answers {string} with {int} photos")
  public void grandmaAnswersWithPhotos(String answer, int photos) {
    grandmaAnswers(HelpRequests.STEP_4.value(), photos, "4e5f6071-8293-4a4b-bdce-3f4a5b6c7d8e");
  }

  @Then("the answer with its {int} photos is announced")
  public void answerWithPhotosIsAnnounced(int photos) {
    JsonNode provided = driver.nextMessage(providedQueue, "HelpProvided");
    assertThat(provided.get("helpProviderType").asText()).isEqualTo("GRANDMA_AVATAR");
    assertThat(provided.get("answer").get("images")).hasSize(photos);
  }

  @Then("Lena's request is answered")
  public void lenasRequestIsAnswered() {
    assertThat(statusOfLenasRequest()).isEqualTo("ANSWERED");
  }

  @When("Jonas and Mia from the Community both answer")
  public void jonasAndMiaAnswer() {
    assertThat(communityAnswers(HelpRequests.JONAS).getStatusCode().value()).isEqualTo(201);
    assertThat(communityAnswers(MIA).getStatusCode().value()).isEqualTo(201);
  }

  @Then("both answers are announced")
  public void bothAnswersAreAnnounced() {
    JsonNode first = driver.nextMessage(providedQueue, "HelpProvided");
    JsonNode second = driver.nextMessage(providedQueue, "HelpProvided");
    assertThat(List.of(first.get("helpProvider").asText(), second.get("helpProvider").asText()))
        .containsExactlyInAnyOrder(HelpRequests.JONAS.value().toString(), MIA.value().toString());
  }

  @Given("Jonas and Mia answered Lena's request about step {int}")
  public void jonasAndMiaAnswered(int step) {
    lenaAskedTheCommunity(step);
    ResponseEntity<String> jonas = communityAnswers(HelpRequests.JONAS);
    ResponseEntity<String> mia = communityAnswers(MIA);
    assertThat(jonas.getStatusCode().value()).isEqualTo(201);
    assertThat(mia.getStatusCode().value()).isEqualTo(201);
    helpsBy.put("Jonas", helpIdOf(jonas));
    helpsBy.put("Mia", helpIdOf(mia));
  }

  @When("Lena accepts Mia's answer")
  public void lenaAcceptsMiasAnswer() {
    lenaAccepts("Mia", "COMMUNITY");
  }

  @Then("the request is closed")
  public void theRequestIsClosed() {
    assertThat(statusOfLenasRequest()).isEqualTo("CLOSED");
    JsonNode accepted = driver.nextMessage(acceptedQueue, "HelpAccepted");
    assertThat(accepted.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(accepted.get("helpId").asText()).isEqualTo(acceptedHelp);
    assertThat(accepted.get("helpProviderType").asText()).isEqualTo(acceptedProviderType);
    assertThat(accepted.get("helpRequestStatus").asText()).isEqualTo("CLOSED");
  }

  @Given("Grandma Avatar answered Lena's request about step {int}")
  public void grandmaAnswered(int step) {
    lenaAskedGrandma(step);
    grandmaAnsweredStep4("71829304-b5c6-4d7e-8f01-6c7d8e9fa0b1");
  }

  @When("Lena accepts Grandma's answer")
  public void lenaAcceptsGrandmasAnswer() {
    lenaAccepts("Grandma", "GRANDMA_AVATAR");
  }

  @Given("Lena asked the Community and Grandma Avatar and Grandma answered")
  public void lenaAskedBothAndGrandmaAnswered() {
    lenaAskedBoth();
    grandmaAnsweredStep4("8293a4b5-c6d7-4e8f-9012-7d8e9fa0b1c2");
  }

  @Then("the request is closed for the Community too")
  public void closedForTheCommunityToo() {
    theRequestIsClosed();
    ResponseEntity<String> jonasLate = communityAnswers(HelpRequests.JONAS);
    assertThat(jonasLate.getStatusCode().value()).as("Jonas answers afterwards").isEqualTo(409);
    assertThat(jonasLate.getBody()).contains("\"code\":\"INVALID_STATE\"");
  }

  // R5 — not accepted: sent again with a note and a new deadline

  @When("on Friday at 20:00 Lena rejects it: {string}")
  public void lenaRejectsGrandmasAnswer(String note) {
    lenaRejectsOnFridayEvening("Grandma", note);
  }

  @Then("the request is sent again to Grandma with the note and the deadline Saturday 08:00")
  public void sentAgainToGrandmaWithTheNote() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(rejected.get("note").asText()).isEqualTo("warm water didn't help");
    assertThat(rejected.get("askedProviders")).hasSize(1);
    assertThat(rejected.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("GRANDMA_AVATAR");
    assertDeadlineIsSaturdayAt0800(
        rejected.get("askedProviders").get(0).get("deadline"), "Grandma");
    JsonNode grandmasSide = readRequest(lenasRequest()).get("askedProviders").get(0);
    assertThat(grandmasSide.get("state").asText()).isEqualTo("ASKED");
    assertDeadlineIsSaturdayAt0800(grandmasSide.get("deadline"), "Grandma");
  }

  @Given("Lena asked the Community and Grandma Avatar and Jonas answered")
  public void lenaAskedBothAndJonasAnswered() {
    lenaAskedBoth();
    ResponseEntity<String> jonas = communityAnswers(HelpRequests.JONAS);
    assertThat(jonas.getStatusCode().value()).isEqualTo(201);
    helpsBy.put("Jonas", helpIdOf(jonas));
  }

  @When("Lena rejects Jonas's answer and accepts nothing")
  public void lenaRejectsJonassAnswer() {
    lenaRejectsOnFridayEvening("Jonas", "it still split");
  }

  @Then("the request is sent again to both")
  public void sentAgainToBoth() {
    JsonNode rejected = driver.nextMessage(rejectedQueue, "HelpRejected");
    assertThat(rejected.get("askedProviders").findValuesAsText("providerType"))
        .containsExactlyInAnyOrder("COMMUNITY", "GRANDMA_AVATAR");
    for (JsonNode asked : rejected.get("askedProviders")) {
      assertDeadlineIsSaturdayAt0800(asked.get("deadline"), asked.get("providerType").asText());
    }
    assertThat(readRequest(lenasRequest()).get("askedProviders").findValuesAsText("state"))
        .containsExactly("ASKED", "ASKED");
  }

  // R6 — notes are shown on future requests for the same step

  @Given("Lena noted {string} on step {int} of her Hollandaise")
  public void lenaNoted(String note, int step) {
    grandmaAnswered(step);
    lenaRejectsOnFridayEvening("Grandma", note);
  }

  @When("next month she asks about step {int} again")
  public void nextMonthSheAsksAgain(int step) {
    clock.moveTo(friday("18:00").plus(Duration.ofDays(28)));
    lenaAskedTheCommunity(step);
  }

  @Then("the note {string} is shown")
  public void theNoteIsShown(String note) {
    String earlier = lenasRequests.get(lenasRequests.size() - 2);
    JsonNode previous = readRequest(lenasRequest()).get("previousNotes");
    assertThat(previous).as("previousNotes of the new request").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo(note);
    assertThat(previous.get(0).get("helpRequestLink").asText())
        .endsWith("/help-requests/" + earlier);
  }

  // R7 — cancelling, with an optional note

  @When("she cancels: {string}")
  public void sheCancelsWithANote(String note) {
    lenaCancels("{\"note\":\"%s\"}".formatted(note));
  }

  @Then("the request is cancelled and the providers see the note")
  public void cancelledAndTheProvidersSeeTheNote() {
    assertThat(statusOfLenasRequest()).isEqualTo("CANCELLED");
    JsonNode cancelled = driver.nextMessage(cancelledQueue, "HelpRequestCancelled");
    assertThat(cancelled.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(cancelled.get("helpRequestStatus").asText()).isEqualTo("CANCELLED");
    assertThat(cancelled.path("note").asText()).isEqualTo("an ice cube saved it");
  }

  @When("she cancels without a note")
  public void sheCancelsWithoutANote() {
    lenaCancels(null);
  }

  @Then("the request is cancelled")
  public void theRequestIsCancelled() {
    assertThat(statusOfLenasRequest()).isEqualTo("CANCELLED");
    JsonNode cancelled = driver.nextMessage(cancelledQueue, "HelpRequestCancelled");
    assertThat(cancelled.get("helpRequest").asText()).isEqualTo(lenasRequest());
    assertThat(cancelled.has("note")).as("a note in HelpRequestCancelled").isFalse();
  }

  // R8 — unanswered after 12 h: cancelled, a provider not yet asked suggested

  @Given("Lena asked only the Community on Friday at 18:00")
  public void lenaAskedOnlyTheCommunityOnFriday() {
    clock.moveTo(friday("18:00"));
    lenaAskedTheCommunity(4);
  }

  /** A few seconds after the deadline: the clock ticked while Lena asked (12 h, INV-09). */
  @When("nobody answers until Saturday 06:00")
  public void nobodyAnswersUntilSaturdayMorning() {
    clock.moveTo(saturday("06:00:05"));
  }

  @Then("the request is cancelled and Grandma Avatar is suggested")
  public void cancelledAndGrandmaSuggested() {
    assertCancelledAfterTimeOut("COMMUNITY", "GRANDMA_AVATAR");
  }

  @Given("Lena asked only Grandma Avatar on Friday at 18:00")
  public void lenaAskedOnlyGrandmaOnFriday() {
    clock.moveTo(friday("18:00"));
    lenaAskedGrandma(4);
  }

  @Then("the request is cancelled and the Community is suggested")
  public void cancelledAndCommunitySuggested() {
    assertCancelledAfterTimeOut("GRANDMA_AVATAR", "COMMUNITY");
  }

  @Given("Lena asked the Community and Grandma Avatar")
  public void lenaAskedBoth() {
    lenaAsksAboutStep4("COMMUNITY", "GRANDMA_AVATAR");
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
  }

  @When("both stay silent for 12 hours")
  public void bothStaySilent() {
    clock.moveTo(friday("18:00").plus(Duration.ofHours(12).plusSeconds(5)));
  }

  /** The time-out message is how Lena is told: nobody is left to suggest (INV-09). */
  @Then("the request is cancelled and Lena is told")
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

  @Given("Lena asked the Community on Friday at 18:00")
  public void lenaAskedTheCommunityOnFriday() {
    clock.moveTo(friday("18:00"));
    lenaAskedTheCommunity(4);
  }

  @When("Jonas answers on Saturday at 05:59")
  public void jonasAnswersAtFiveFiftyNine() {
    clock.moveTo(saturday("05:59"));
    lastResponse = communityAnswers(HelpRequests.JONAS);
  }

  @Then("his answer counts")
  public void hisAnswerCounts() {
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(201);
    JsonNode provided = driver.nextMessage(providedQueue, "HelpProvided");
    assertThat(provided.get("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
    assertThat(statusOfLenasRequest()).isEqualTo("ANSWERED");
    clock.moveTo(saturday("06:00:05"));
    driver.noMessage(timedOutQueue);
    assertThat(statusOfLenasRequest()).as("after the deadline").isEqualTo("ANSWERED");
  }

  // S3 — answers must fit the request

  @When("Lena tries to answer her own request")
  public void lenaTriesToAnswerHerOwnRequest() {
    lastResponse = communityAnswers(HelpRequests.LENA);
  }

  @Then("she is refused because it is her own request")
  public void refusedBecauseOwnRequest() {
    assertThat(lastResponse.getStatusCode().value()).isEqualTo(403);
    assertThat(lastResponse.getBody()).contains("\"code\":\"OWN_REQUEST\"");
  }

  @When("Grandma Avatar answers with an explanation of step {int}")
  public void grandmaAnswersAnotherStep(int step) {
    grandmaAnswers(STEP_6, 0, "5f607182-93a4-4b5c-8edf-4a5b6c7d8e9f");
  }

  @When("Grandma Avatar answers with {int} photos")
  public void grandmaAnswersWithTooManyPhotos(int photos) {
    grandmaAnswers(HelpRequests.STEP_4.value(), photos, "607182a3-a4b5-4c6d-9ef0-5b6c7d8e9fa0");
  }

  @Then("Grandma's answer is refused and set aside")
  public void grandmasAnswerIsSetAside() {
    assertThat(driver.nextDeadLetter()).contains(lenasRequest());
  }

  @Then("Lena's request is still open")
  public void lenasRequestIsStillOpen() {
    assertThat(statusOfLenasRequest()).isEqualTo("OPEN");
  }
}
