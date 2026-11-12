package org.larder.cookingassistance.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.in.rest.SpecSchemas;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.IngredientId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * getHelpRequestById through the real web layer and the JDBC query adapter on PostgreSQL: the query
 * reads the aggregate's tables, never the aggregate (ADR0016, read-your-writes QS-07).
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, TestSigningKeys.class})
class HelpRequestQueryIT {

  static final String JONAS_READING =
      TestTokens.forCook(HelpRequests.JONAS).roles("cook", "community").scope("help:read").bearer();

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;
  @Autowired HelpRequestRepository helpRequests;

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  private String read(String helpRequestId, String bearer, int expectedStatus) throws Exception {
    return mvc.perform(
            get("/help-requests/" + helpRequestId)
                .header(AUTHORIZATION, bearer)
                .header("version", "1.0.3"))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  static final HelpRequestId NEXT_MONTH_REQUEST_ID =
      new HelpRequestId(UUID.fromString("01a241ff-9a80-7000-8000-000000000004"));
  static final HelpRequestId NOVEMBER_REQUEST_ID =
      new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000001"));

  /** Lena asks Grandma about step 4 of her Hollandaise on Friday 6 November (UUIDv7 of 17:00Z). */
  private static HelpRequest lenaAsksAboutStep4InNovember() {
    return HelpRequest.requestHelpWithPreparationStep(
        NOVEMBER_REQUEST_ID,
        HelpRequests.LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HelpRequests.HOLLANDAISE,
        HelpRequests.STEP_4,
        Set.of(HelpProviderType.GRANDMA_AVATAR),
        HelpRequests.FRIDAY_18_00);
  }

  /** Lena asks the Community about step 4 of her Hollandaise again, a month later. */
  private static HelpRequest lenaAsksAboutStep4AgainNextMonth() {
    return HelpRequest.requestHelpWithPreparationStep(
        NEXT_MONTH_REQUEST_ID,
        HelpRequests.LENA,
        "Hollandaise again",
        "Step 4 still splits",
        HelpRequests.HOLLANDAISE,
        HelpRequests.STEP_4,
        Set.of(HelpProviderType.COMMUNITY),
        Clock.fixed(Instant.parse("2026-12-04T17:00:00Z"), ZoneOffset.UTC));
  }

  static final HowToStepId STEP_6 =
      new HowToStepId(UUID.fromString("5d1c8e2a-6f3b-4a7e-9c0d-1b2a3c4d5e66"));
  static final RecipeId BEARNAISE =
      new RecipeId(UUID.fromString("8b2e4f60-1c3d-4e5f-a6b7-c8d9e0f1a2b3"));

  /** An earlier request of the given Cook on the given step, cancelled with a note. */
  private static HelpRequest cancelledEarlier(
      String id, CookId cook, RecipeId recipe, HowToStepId step) {
    HelpRequest earlier =
        HelpRequest.requestHelpWithPreparationStep(
            new HelpRequestId(UUID.fromString(id)),
            cook,
            "Splitting sauce",
            "It splits",
            recipe,
            step,
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    earlier.cancel(Optional.of("an ice cube saved it"), HelpRequests.FRIDAY_18_10);
    return earlier;
  }

  private String list(String query, String bearer, int expectedStatus) throws Exception {
    return mvc.perform(
            get("/help-requests" + query).header(AUTHORIZATION, bearer).header("version", "1.0.8"))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> ids(String pageBody) throws Exception {
    return new ObjectMapper().readTree(pageBody).get("items").findValuesAsText("helpRequestId");
  }

  /** A request of Lena's to the Community about step 4 of her Hollandaise, with the given id. */
  private static HelpRequest lenasRequest(String id) {
    return HelpRequest.requestHelpWithPreparationStep(
        new HelpRequestId(UUID.fromString(id)),
        HelpRequests.LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HelpRequests.HOLLANDAISE,
        HelpRequests.STEP_4,
        Set.of(HelpProviderType.COMMUNITY),
        HelpRequests.FRIDAY_18_00);
  }

  private static List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  @DisplayName(
      "Lena's stored request to COMMUNITY, read with help:read -> 200 with helpRequestId, requester, title, type PSE, description, recipe, howToStep, preferredProvider [COMMUNITY], askedProviders [{COMMUNITY, deadline = request time + 12 h, ASKED}], status OPEN; matches the HelpRequest schema, no null and no unbuilt optional field -- B3-S01 US-CA-03 op getHelpRequestById")
  @Test
  void lenasRequestIsReadBack() throws Exception {
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    helpRequests.save(lenas);

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode request = new ObjectMapper().readTree(body);
    assertThat(fieldNames(request))
        .containsExactlyInAnyOrder(
            "helpRequestId",
            "requester",
            "title",
            "type",
            "description",
            "recipe",
            "howToStep",
            "preferredProvider",
            "askedProviders",
            "status",
            "helpsLink");
    assertThat(request.get("helpRequestId").asText())
        .isEqualTo(HelpRequests.HELP_REQUEST_ID.value().toString());
    assertThat(request.get("requester").asText()).isEqualTo(HelpRequests.LENA.value().toString());
    assertThat(request.get("title").asText()).isEqualTo(lenas.title());
    assertThat(request.get("type").asText()).isEqualTo("PREPARATION_STEP_EXPLANATION");
    assertThat(request.get("description").asText()).isEqualTo(lenas.description());
    assertThat(request.get("recipe").asText())
        .isEqualTo(HelpRequests.HOLLANDAISE.value().toString());
    assertThat(request.get("howToStep").asText()).isEqualTo(HelpRequests.STEP_4.value().toString());
    assertThat(request.get("preferredProvider").toString()).isEqualTo("[\"COMMUNITY\"]");
    JsonNode asked = request.get("askedProviders").get(0);
    assertThat(asked.get("providerType").asText()).isEqualTo("COMMUNITY");
    assertThat(java.time.Instant.parse(asked.get("deadline").asText()))
        .isEqualTo(lenas.askedProviders().getFirst().deadline());
    assertThat(asked.get("state").asText()).isEqualTo("ASKED");
    assertThat(request.get("status").asText()).isEqualTo("OPEN");
    assertThat(request.path("helpsLink").asText())
        .isEqualTo(
            "http://localhost/help-requests/" + HelpRequests.HELP_REQUEST_ID.value() + "/helps");
  }

  @DisplayName(
      "after Grandma's answer is stored -> status ANSWERED, GRANDMA_AVATAR state ANSWERED -- B3-S02 US-CA-03 op getHelpRequestById")
  @Test
  void answeredRequestShowsGrandmasSide() throws Exception {
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    helpRequests.save(lenas);
    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.grandmaExplainsStep4(stored);
    helpRequests.save(stored);

    JsonNode request =
        new ObjectMapper()
            .readTree(read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200));

    assertThat(request.get("status").asText()).isEqualTo("ANSWERED");
    assertThat(request.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("GRANDMA_AVATAR");
    assertThat(request.get("askedProviders").get(0).get("state").asText()).isEqualTo("ANSWERED");
  }

  @DisplayName("unknown id -> 404 NOT_FOUND Problem -- B3-S01 US-CA-03 op getHelpRequestById")
  @Test
  void unknownHelpRequestIsNotFound() throws Exception {
    mvc.perform(
            get("/help-requests/00000000-0000-7000-8000-000000000009")
                .header(AUTHORIZATION, JONAS_READING)
                .header("version", "1.0.3"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "helpRequestId \"not-a-uuid\" -> 400 VALIDATION_FAILED Problem -- B3-S01 US-CA-03 op getHelpRequestById")
  @Test
  void idThatIsNoUuidIsABadRequest() throws Exception {
    mvc.perform(
            get("/help-requests/not-a-uuid")
                .header(AUTHORIZATION, JONAS_READING)
                .header("version", "1.0.3"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @DisplayName(
      "no token -> 401 UNAUTHENTICATED; token with help:write only -> 403 INSUFFICIENT_SCOPE -- B3-S01 US-CA-03 ADR0012 ADR0013 op getHelpRequestById")
  @Test
  void readNeedsTheReadScope() throws Exception {
    String id = HelpRequests.HELP_REQUEST_ID.value().toString();
    mvc.perform(get("/help-requests/" + id).header("version", "1.0.3"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            get("/help-requests/" + id)
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.JONAS)
                        .roles("cook", "community")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.3"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
  }

  @DisplayName(
      "POST /help-requests, then GET the returned helpRequestLink at once -> 200 with that request (read-your-writes) -- B3-S01 US-CA-03 QS-07 ADR0016 op createHelpRequest,getHelpRequestById")
  @Test
  void cookReadsHerOwnWriteAtOnce() throws Exception {
    String created =
        mvc.perform(
                post("/help-requests")
                    .header(
                        AUTHORIZATION,
                        TestTokens.forCook(HelpRequests.LENA)
                            .roles("cook")
                            .scope("help:read help:write")
                            .bearer())
                    .header("version", "1.0.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "title": "Sauce splits",
                          "type": "PREPARATION_STEP_EXPLANATION",
                          "description": "My hollandaise splits at step 4",
                          "recipe": "%s",
                          "howToStep": "%s",
                          "preferredProvider": ["COMMUNITY"]
                        }
                        """
                            .formatted(
                                HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value())))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode link = new ObjectMapper().readTree(created);

    JsonNode request =
        new ObjectMapper().readTree(read(link.get("helpRequestId").asText(), JONAS_READING, 200));

    assertThat(link.get("helpRequestLink").asText())
        .isEqualTo("http://localhost/help-requests/" + link.get("helpRequestId").asText());
    assertThat(request.get("helpRequestId").asText()).isEqualTo(link.get("helpRequestId").asText());
    assertThat(request.get("requester").asText()).isEqualTo(HelpRequests.LENA.value().toString());
  }

  @DisplayName(
      "Q1 Lena cancelled with 'an ice cube saved it' (Fri 21:00) -> notes [{kind CANCELLATION, text 'an ice cube saved it', writtenAt Fri 21:00}] -- B3-S03 US-CA-03 INV-08 R7.E1 op getHelpRequestById")
  @Test
  void cancellationNoteIsShown() throws Exception {
    Clock friday2100 = Clock.fixed(Instant.parse("2026-11-06T20:00:00Z"), ZoneOffset.UTC);
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    lenas.cancel(Optional.of("an ice cube saved it"), friday2100);
    helpRequests.save(lenas);

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode notes = new ObjectMapper().readTree(body).get("notes");
    assertThat(notes).as("notes").isNotNull().hasSize(1);
    assertThat(fieldNames(notes.get(0))).containsExactlyInAnyOrder("kind", "text", "writtenAt");
    assertThat(notes.get(0).get("kind").asText()).isEqualTo("CANCELLATION");
    assertThat(notes.get(0).get("text").asText()).isEqualTo("an ice cube saved it");
    assertThat(Instant.parse(notes.get(0).get("writtenAt").asText()))
        .isEqualTo(friday2100.instant());
  }

  @DisplayName(
      "Q2 Lena rejected Grandma's Help (Fri 20:00 'warm water didn't help') -> notes [{kind REJECTION, text 'warm water didn't help', writtenAt Fri 20:00}] -- B3-S05 US-CA-03 INV-06 R5.E1 op getHelpRequestById")
  @Test
  void rejectionNoteIsShown() throws Exception {
    Clock friday2000 = Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC);
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(lenas);
    lenas.reject(HelpRequests.HELP_ID, "warm water didn't help", friday2000);
    helpRequests.save(lenas);

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode notes = new ObjectMapper().readTree(body).get("notes");
    assertThat(notes).as("notes").isNotNull().hasSize(1);
    assertThat(fieldNames(notes.get(0))).containsExactlyInAnyOrder("kind", "text", "writtenAt");
    assertThat(notes.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(notes.get(0).get("text").asText()).isEqualTo("warm water didn't help");
    assertThat(Instant.parse(notes.get(0).get("writtenAt").asText()))
        .isEqualTo(friday2000.instant());
  }

  @DisplayName(
      "Q3 Lena's earlier request on step 4 of the Hollandaise carries the rejection note; her new request on step 4 -> previousNotes [{REJECTION 'warm water didn't help', Fri 20:00, helpRequestLink <base>/help-requests/{earlierId}}] -- B3-S05 US-CA-03 INV-07 R6.E1 op getHelpRequestById")
  @Test
  void previousNotesOfTheSameStepAreShown() throws Exception {
    Clock friday2000 = Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC);
    HelpRequest earlier = lenaAsksAboutStep4InNovember();
    HelpRequests.grandmaExplainsStep4(earlier);
    earlier.reject(HelpRequests.HELP_ID, "warm water didn't help", friday2000);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutStep4AgainNextMonth());

    String body = read(NEXT_MONTH_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode previous = new ObjectMapper().readTree(body).get("previousNotes");
    assertThat(previous).as("previousNotes").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo("warm water didn't help");
    assertThat(Instant.parse(previous.get(0).get("writtenAt").asText()))
        .isEqualTo(friday2000.instant());
    assertThat(previous.get(0).get("helpRequestLink").asText())
        .isEqualTo("http://localhost/help-requests/" + NOVEMBER_REQUEST_ID.value());
  }

  @DisplayName(
      "P1 Lena has cancelled 12 earlier requests on step 4 with a note each -> previousNotes are the 10 most recent, oldest first: a page stays bounded however many requests she makes -- B3-S01 US-CA-03 INV-07 OWASP-API4 SEC-14")
  @Test
  void previousNotesAreBoundedToTheMostRecent() throws Exception {
    for (int n = 1; n <= 12; n++) {
      helpRequests.save(
          cancelledEarlier(
              earlierId(n), HelpRequests.LENA, HelpRequests.HOLLANDAISE, HelpRequests.STEP_4));
    }
    helpRequests.save(lenaAsksAboutStep4AgainNextMonth());

    String body = read(NEXT_MONTH_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(
            new ObjectMapper()
                .readTree(body)
                .get("previousNotes")
                .findValuesAsText("helpRequestLink"))
        .containsExactlyElementsOf(
            IntStream.rangeClosed(3, 12)
                .mapToObj(n -> "http://localhost/help-requests/" + earlierId(n))
                .toList());
  }

  private static String earlierId(int n) {
    return "019a6f00-0000-7000-8000-%012d".formatted(n);
  }

  @DisplayName(
      "Q4 previousNotes leave out a note on another step, another recipe, another Cook's request and the request's own notes -> previousNotes absent -- B3-S05 US-CA-03 INV-07 op getHelpRequestById")
  @Test
  void previousNotesLeaveOutOtherSteps() throws Exception {
    helpRequests.save(
        cancelledEarlier(
            "019a6f00-0000-7000-8000-000000000001",
            HelpRequests.LENA,
            HelpRequests.HOLLANDAISE,
            STEP_6));
    helpRequests.save(
        cancelledEarlier(
            "019a6f00-0000-7000-8000-000000000002",
            HelpRequests.LENA,
            BEARNAISE,
            HelpRequests.STEP_4));
    helpRequests.save(
        cancelledEarlier(
            "019a6f00-0000-7000-8000-000000000003",
            HelpRequests.MIA,
            HelpRequests.HOLLANDAISE,
            HelpRequests.STEP_4));
    HelpRequest nextMonth = lenaAsksAboutStep4AgainNextMonth();
    HelpRequests.communityMemberExplainsStep4(
        nextMonth, HelpRequests.OTHER_HELP_ID, HelpRequests.JONAS);
    nextMonth.reject(
        HelpRequests.OTHER_HELP_ID,
        "still too hot",
        Clock.fixed(Instant.parse("2026-12-04T18:00:00Z"), ZoneOffset.UTC));
    helpRequests.save(nextMonth);

    String body = read(NEXT_MONTH_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    assertThat(new ObjectMapper().readTree(body).get("previousNotes")).as("previousNotes").isNull();
  }

  @DisplayName(
      "Q4b reading Lena's November request leaves out the notes of her December request on the same step -> previousNotes absent -- B3-S05 US-CA-03 INV-07 ADR0016 L6 op getHelpRequestById")
  @Test
  void previousNotesLeaveOutLaterRequests() throws Exception {
    helpRequests.save(lenaAsksAboutStep4InNovember());
    HelpRequest december = lenaAsksAboutStep4AgainNextMonth();
    HelpRequests.communityMemberExplainsStep4(
        december, HelpRequests.OTHER_HELP_ID, HelpRequests.JONAS);
    december.reject(
        HelpRequests.OTHER_HELP_ID,
        "still too hot",
        Clock.fixed(Instant.parse("2026-12-04T18:00:00Z"), ZoneOffset.UTC));
    helpRequests.save(december);

    String body = read(NOVEMBER_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    assertThat(new ObjectMapper().readTree(body).get("previousNotes")).as("previousNotes").isNull();
  }

  @DisplayName(
      "Q5 helpsLink <base>/help-requests/{id}/helps on every request; acceptedHelpLink <base>/help-requests/{id}/helps/{helpId} only once CLOSED, absent while OPEN -- B3-S04 US-CA-03 INV-05 op getHelpRequestById")
  @Test
  void linksToTheHelps() throws Exception {
    String helps = "http://localhost/help-requests/" + HelpRequests.HELP_REQUEST_ID.value();
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(lenas);
    helpRequests.save(lenas);

    JsonNode answered =
        new ObjectMapper()
            .readTree(read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200));
    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    stored.accept(HelpRequests.HELP_ID, HelpRequests.FRIDAY_18_10);
    helpRequests.save(stored);
    String closedBody = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(answered.path("helpsLink").asText()).isEqualTo(helps + "/helps");
    assertThat(answered.get("acceptedHelpLink")).as("acceptedHelpLink while ANSWERED").isNull();
    SpecSchemas.assertMatches(closedBody, "HelpRequest");
    JsonNode closed = new ObjectMapper().readTree(closedBody);
    assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
    assertThat(closed.path("helpsLink").asText()).isEqualTo(helps + "/helps");
    assertThat(closed.path("acceptedHelpLink").asText())
        .as("acceptedHelpLink once CLOSED")
        .isEqualTo(helps + "/helps/" + HelpRequests.HELP_ID.value());
  }

  @DisplayName(
      "Q6 COMMUNITY timed out -> status CANCELLED, askedProviders [{COMMUNITY, TIMED_OUT}], suggestedProviders [GRANDMA_AVATAR] -- B3-S06 US-CA-03 INV-09 R8.E1 op getHelpRequestById")
  @Test
  void timedOutSuggestsGrandma() throws Exception {
    HelpRequest lenas = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    lenas.timeOut(Clock.fixed(Instant.parse("2026-11-07T05:00:01Z"), ZoneOffset.UTC));
    helpRequests.save(lenas);

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode request = new ObjectMapper().readTree(body);
    assertThat(request.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(request.get("askedProviders").get(0).get("providerType").asText())
        .isEqualTo("COMMUNITY");
    assertThat(request.get("askedProviders").get(0).get("state").asText()).isEqualTo("TIMED_OUT");
    assertThat(request.path("suggestedProviders").toString()).isEqualTo("[\"GRANDMA_AVATAR\"]");
  }

  @DisplayName(
      "Q7 an OPEN request without notes or suggestions -> notes, previousNotes, suggestedProviders and acceptedHelpLink absent, never null -- B3-S01 US-CA-03 op getHelpRequestById")
  @Test
  void unfilledListsAreAbsent() throws Exception {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    JsonNode request =
        new ObjectMapper()
            .readTree(read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200));

    assertThat(fieldNames(request))
        .doesNotContain("notes", "previousNotes", "suggestedProviders", "acceptedHelpLink");
  }

  @DisplayName(
      "L1 three requests, no filter -> 200 items oldest first (creation order), no nextCursor when all fit; each item matches the HelpRequest schema -- B3-S01 US-CA-03 ADR0016 L6 op listHelpRequests")
  @Test
  void listsOldestFirst() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000c"));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000b"));

    String body = list("", JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequestPage");
    JsonNode page = new ObjectMapper().readTree(body);
    assertThat(page.get("items").findValuesAsText("helpRequestId"))
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a",
            "01a1b1cd-8a80-7000-8000-00000000000b",
            "01a1b1cd-8a80-7000-8000-00000000000c");
    assertThat(page.get("nextCursor")).as("nextCursor").isNull();
  }

  @DisplayName(
      "L2 requester=Lena -> only Lena's; status=OPEN -> only OPEN; type=PREPARATION_STEP_EXPLANATION -> all; requester=Lena and status=CANCELLED -> only Lena's cancelled -- B3-S03 US-CA-03 op listHelpRequests")
  @Test
  void filtersNarrowTheList() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));
    HelpRequest cancelled = lenasRequest("01a1b1cd-8a80-7000-8000-00000000000b");
    cancelled.cancel(Optional.empty(), HelpRequests.FRIDAY_18_10);
    helpRequests.save(cancelled);
    helpRequests.save(
        HelpRequest.requestHelpWithPreparationStep(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-00000000000c")),
            HelpRequests.MIA,
            "Splitting sauce",
            "It splits",
            HelpRequests.HOLLANDAISE,
            HelpRequests.STEP_4,
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00));

    assertThat(ids(list("?requester=" + HelpRequests.LENA.value(), JONAS_READING, 200)))
        .as("requester=Lena")
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a", "01a1b1cd-8a80-7000-8000-00000000000b");
    assertThat(ids(list("?status=OPEN", JONAS_READING, 200)))
        .as("status=OPEN")
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a", "01a1b1cd-8a80-7000-8000-00000000000c");
    assertThat(ids(list("?type=PREPARATION_STEP_EXPLANATION", JONAS_READING, 200)))
        .as("type=PREPARATION_STEP_EXPLANATION")
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a",
            "01a1b1cd-8a80-7000-8000-00000000000b",
            "01a1b1cd-8a80-7000-8000-00000000000c");
    assertThat(
            ids(
                list(
                    "?requester=" + HelpRequests.LENA.value() + "&status=CANCELLED",
                    JONAS_READING,
                    200)))
        .as("requester=Lena and status=CANCELLED")
        .containsExactly("01a1b1cd-8a80-7000-8000-00000000000b");
  }

  @DisplayName(
      "L3 limit=2 of three -> two items and a nextCursor; following it -> the third, no nextCursor -- B3-S01 US-CA-03 ADR0016 L6 op listHelpRequests")
  @Test
  void pagesFollowTheCursor() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000b"));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000c"));

    JsonNode first = new ObjectMapper().readTree(list("?limit=2", JONAS_READING, 200));
    String cursor = first.path("nextCursor").asText();
    String secondBody = list("?limit=2&cursor=" + cursor, JONAS_READING, 200);

    assertThat(first.get("items").findValuesAsText("helpRequestId"))
        .as("page 1")
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a", "01a1b1cd-8a80-7000-8000-00000000000b");
    assertThat(cursor).as("nextCursor of page 1").isNotEmpty();
    SpecSchemas.assertMatches(secondBody, "HelpRequestPage");
    JsonNode second = new ObjectMapper().readTree(secondBody);
    assertThat(second.get("items").findValuesAsText("helpRequestId"))
        .as("page 2")
        .containsExactly("01a1b1cd-8a80-7000-8000-00000000000c");
    assertThat(second.get("nextCursor")).as("nextCursor of page 2").isNull();
  }

  @DisplayName(
      "L4 a request created after page 1 was read appears on the next page, never on page 1; no item twice -- B3-S01 US-CA-03 ADR0016 L6 op listHelpRequests")
  @Test
  void laterRequestsComeOnLaterPages() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000b"));

    JsonNode page1 = new ObjectMapper().readTree(list("?limit=1", JONAS_READING, 200));
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000c"));
    JsonNode page2 =
        new ObjectMapper()
            .readTree(
                list("?limit=1&cursor=" + page1.path("nextCursor").asText(), JONAS_READING, 200));
    JsonNode page3 =
        new ObjectMapper()
            .readTree(
                list("?limit=1&cursor=" + page2.path("nextCursor").asText(), JONAS_READING, 200));

    List<String> seen = new ArrayList<>();
    seen.addAll(page1.get("items").findValuesAsText("helpRequestId"));
    seen.addAll(page2.get("items").findValuesAsText("helpRequestId"));
    seen.addAll(page3.get("items").findValuesAsText("helpRequestId"));
    assertThat(seen)
        .as("pages 1-3")
        .containsExactly(
            "01a1b1cd-8a80-7000-8000-00000000000a",
            "01a1b1cd-8a80-7000-8000-00000000000b",
            "01a1b1cd-8a80-7000-8000-00000000000c");
    assertThat(page3.get("nextCursor")).as("nextCursor of page 3").isNull();
  }

  @DisplayName(
      "L5 status=FOO, limit=0, limit=101 -> 400 VALIDATION_FAILED Problem -- B3-S01 US-CA-03 Q-02 op listHelpRequests")
  @Test
  void invalidFiltersAreRefused() throws Exception {
    for (String query : List.of("?status=FOO", "?limit=0", "?limit=101")) {
      mvc.perform(
              get("/help-requests" + query)
                  .header(AUTHORIZATION, JONAS_READING)
                  .header("version", "1.0.8"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @DisplayName(
      "L6 cursor 'abc', a base64url value that is no id, a cursor longer than the spec allows -> 400 VALIDATION_FAILED Problem, not 500 -- B3-S01 US-CA-03 ADR0016 L6 op listHelpRequests")
  @Test
  void foreignCursorIsRefused() throws Exception {
    String notAnId =
        java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("warm water".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    for (String cursor : List.of("abc", notAnId, "a".repeat(501))) {
      mvc.perform(
              get("/help-requests")
                  .param("cursor", cursor)
                  .header(AUTHORIZATION, JONAS_READING)
                  .header("version", "1.0.8"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
  }

  @DisplayName(
      "L7 no token -> 401 UNAUTHENTICATED; token with help:write only -> 403 INSUFFICIENT_SCOPE -- B3-S01 US-CA-03 ADR0012 ADR0013 op listHelpRequests")
  @Test
  void listNeedsTheReadScope() throws Exception {
    mvc.perform(get("/help-requests").header("version", "1.0.8"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            get("/help-requests")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.JONAS)
                        .roles("cook", "community")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.8"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
  }

  @DisplayName(
      "L9 type=INGREDIENT_SUBSTITUTE, which the spec allows but no request has yet -> 200 with no items, not 500 -- B3-S01 US-CA-03 op listHelpRequests (step 11 r1 from step 12, Schemathesis)")
  @Test
  void typeNoRequestHasYetListsNothing() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));

    String body = list("?type=INGREDIENT_SUBSTITUTE", JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequestPage");
    assertThat(new ObjectMapper().readTree(body).get("items")).isEmpty();
  }

  @DisplayName(
      "Lena reads her ingredient request -> 200 with type INGREDIENT_SUBSTITUTE, recipe, ingredient, substituteRequirements, no howToStep, valid against the HelpRequest schema -- B2-S01 US-CA-02 INV-11 op getHelpRequestById")
  @Test
  void ingredientRequestIsReadBack() throws Exception {
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode request = new ObjectMapper().readTree(body);
    assertThat(request.get("type").asText()).isEqualTo("INGREDIENT_SUBSTITUTE");
    assertThat(request.get("recipe").asText())
        .isEqualTo(HelpRequests.TOMATO_SOUP.value().toString());
    assertThat(request.get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
    assertThat(request.get("substituteRequirements").asText()).isEqualTo("200 g, not available");
    assertThat(request.has("howToStep")).isFalse();
  }

  @DisplayName(
      "a preparation-step request is read back without ingredient and substituteRequirements -- B3-S01 US-CA-03 INV-02 (regression) op getHelpRequestById")
  @Test
  void aPreparationStepRequestShowsNoIngredient() throws Exception {
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode request = new ObjectMapper().readTree(body);
    assertThat(request.get("type").asText()).isEqualTo("PREPARATION_STEP_EXPLANATION");
    assertThat(request.has("ingredient")).isFalse();
    assertThat(request.has("ingredientName")).isFalse();
    assertThat(request.has("substituteRequirements")).isFalse();
  }

  @DisplayName(
      "listHelpRequests with type INGREDIENT_SUBSTITUTE -> only the ingredient request, with its ingredient -- B2-S01 US-CA-02 op listHelpRequests")
  @Test
  void theListShowsIngredientRequests() throws Exception {
    helpRequests.save(lenasRequest("01a1b1cd-8a80-7000-8000-00000000000a"));
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));

    String body = list("?type=INGREDIENT_SUBSTITUTE", JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequestPage");
    assertThat(ids(body)).containsExactly(HelpRequests.HELP_REQUEST_ID.value().toString());
    assertThat(new ObjectMapper().readTree(body).get("items").get(0).get("ingredient").asText())
        .isEqualTo(HelpRequests.CREME_FRAICHE.value().toString());
  }

  static final HelpRequestId MUSHROOM_SAUCE_REQUEST_ID =
      new HelpRequestId(UUID.fromString("3b3c4d5e-0000-7000-8000-000000000016"));
  static final RecipeId MUSHROOM_SAUCE =
      new RecipeId(UUID.fromString("4c5d6e7f-8091-4a2b-b3c4-d5e6f7a8b9c0"));
  static final IngredientId CREME_FRAICHE_IN_MUSHROOM_SAUCE =
      new IngredientId(UUID.fromString("5d6e7f80-91a2-4b3c-8d4e-5f6a7b8c9d0e"));

  /** A week later, Lena asks for a substitute for the given ingredient in her mushroom sauce. */
  private static HelpRequest lenaAsksAboutMushroomSauceNextWeek(String ingredientName) {
    return HelpRequest.requestHelpWithIngredient(
        MUSHROOM_SAUCE_REQUEST_ID,
        HelpRequests.LENA,
        "Creme fraiche in mushroom sauce",
        "Still none in the shop",
        MUSHROOM_SAUCE,
        CREME_FRAICHE_IN_MUSHROOM_SAUCE,
        ingredientName,
        "150 g",
        Set.of(HelpProviderType.COMMUNITY),
        Clock.fixed(Instant.parse("2026-11-16T17:00:00Z"), ZoneOffset.UTC));
  }

  @DisplayName(
      "Lena rejected mascarpone \"too heavy\" on creme fraiche in tomato soup; next week she asks about creme fraiche in mushroom sauce -> previousNotes holds \"too heavy\" -- B3-S05 US-CA-02 INV-15 R5.E3 S4 op getHelpRequestById")
  @Test
  void previousNotesOfTheSameIngredientAreShown() throws Exception {
    Clock monday2000 = Clock.fixed(Instant.parse("2026-11-09T19:00:00Z"), ZoneOffset.UTC);
    HelpRequest earlier = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(earlier, HelpRequests.gramsOf("mascarpone", "200"));
    earlier.reject(HelpRequests.HELP_ID, "too heavy", monday2000);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode previous = new ObjectMapper().readTree(body).get("previousNotes");
    assertThat(previous).as("previousNotes").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo("too heavy");
    assertThat(previous.get(0).get("helpRequestLink").asText())
        .isEqualTo("http://localhost/help-requests/" + HelpRequests.HELP_REQUEST_ID.value());
  }

  @DisplayName(
      "an earlier note about an ingredient of another name, even with the same recipe -> not in previousNotes -- B3-S05 US-CA-02 INV-15 S4 op getHelpRequestById")
  @Test
  void previousNotesLeaveOutAnotherIngredientOfTheSameRecipe() throws Exception {
    HelpRequest earlier =
        HelpRequest.requestHelpWithIngredient(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000021")),
            HelpRequests.LENA,
            "Butter in tomato soup",
            "No butter left",
            HelpRequests.TOMATO_SOUP,
            new IngredientId(UUID.fromString("6e7f8091-a2b3-4c4d-9e5f-6a7b8c9d0e1f")),
            "butter",
            "30 g",
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    earlier.cancel(Optional.of("olive oil did it"), HelpRequests.FRIDAY_18_10);
    helpRequests.save(earlier);
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));

    String body = read(HelpRequests.HELP_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(new ObjectMapper().readTree(body).has("previousNotes"))
        .as("previousNotes")
        .isFalse();
  }

  @DisplayName(
      "Lena cancelled an earlier ingredient request without a note -> nothing in previousNotes of her later request -- B3-S03 US-CA-02 INV-16 S4 op getHelpRequestById")
  @Test
  void aCancellationWithoutANoteCarriesNothing() throws Exception {
    HelpRequest earlier = HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY);
    earlier.cancel(Optional.empty(), HelpRequests.MONDAY_18_00);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(new ObjectMapper().readTree(body).has("previousNotes"))
        .as("previousNotes")
        .isFalse();
  }

  @DisplayName(
      "Lena cancelled an earlier ingredient request with a note -> previousNotes of her later request about that ingredient holds it -- B3-S03 US-CA-02 INV-16 S4 op getHelpRequestById")
  @Test
  void aCancellationNoteOfTheSameIngredientIsShown() throws Exception {
    HelpRequest earlier = HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY);
    earlier.cancel(Optional.of("sour cream curdled"), HelpRequests.MONDAY_18_00);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    SpecSchemas.assertMatches(body, "HelpRequest");
    JsonNode previous = new ObjectMapper().readTree(body).get("previousNotes");
    assertThat(previous).as("previousNotes").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("CANCELLATION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo("sour cream curdled");
  }

  @DisplayName(
      "an earlier note about another ingredient -> not in previousNotes -- B3-S05 US-CA-02 INV-15 op getHelpRequestById")
  @Test
  void previousNotesLeaveOutOtherIngredients() throws Exception {
    HelpRequest earlier =
        HelpRequest.requestHelpWithIngredient(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000022")),
            HelpRequests.LENA,
            "Butter in Bearnaise",
            "No butter left",
            BEARNAISE,
            new IngredientId(UUID.fromString("7f8091a2-b3c4-4d5e-8f6a-7b8c9d0e1f2a")),
            "butter",
            "200 g",
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    earlier.cancel(Optional.of("ghee worked"), HelpRequests.FRIDAY_18_10);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(new ObjectMapper().readTree(body).has("previousNotes"))
        .as("previousNotes")
        .isFalse();
  }

  static final CookId TOM = new CookId(UUID.fromString("0e1d2c3b-4a59-4678-8a9b-0c1d2e3f4a5b"));

  @DisplayName(
      "Tom's earlier note about the same ingredient -> not in Lena's previousNotes -- B3-S05 US-CA-02 INV-15 op getHelpRequestById")
  @Test
  void previousNotesLeaveOutOtherCooks() throws Exception {
    HelpRequest tomsEarlier =
        HelpRequest.requestHelpWithIngredient(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000023")),
            TOM,
            "Creme fraiche in tomato soup",
            "The shop has none",
            HelpRequests.TOMATO_SOUP,
            HelpRequests.CREME_FRAICHE,
            "creme fraiche",
            "200 g",
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    tomsEarlier.cancel(Optional.of("yogurt split"), HelpRequests.FRIDAY_18_10);
    helpRequests.save(tomsEarlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(new ObjectMapper().readTree(body).has("previousNotes"))
        .as("previousNotes")
        .isFalse();
  }

  @DisplayName(
      "an earlier ingredient note -> not in the previousNotes of a preparation-step request -- B3-S01 US-CA-03 INV-07 (regression) op getHelpRequestById")
  @Test
  void aPreparationStepLeavesOutIngredientNotes() throws Exception {
    HelpRequest earlier =
        HelpRequest.requestHelpWithIngredient(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000024")),
            HelpRequests.LENA,
            "Butter in Hollandaise",
            "No butter left",
            HelpRequests.HOLLANDAISE,
            new IngredientId(UUID.fromString("8091a2b3-c4d5-4e6f-9a7b-8c9d0e1f2a3b")),
            "butter",
            "250 g",
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    earlier.cancel(Optional.of("ghee worked"), HelpRequests.FRIDAY_18_10);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutStep4AgainNextMonth());

    String body = read(NEXT_MONTH_REQUEST_ID.value().toString(), JONAS_READING, 200);

    assertThat(new ObjectMapper().readTree(body).has("previousNotes"))
        .as("previousNotes")
        .isFalse();
  }

  @DisplayName(
      "Lena rejected mascarpone on an earlier request; on her later request Jonas proposes mascarpone -> 201, the note is only shown -- B2-S02 US-CA-02 INV-15 R5.E3 S4 op provideHelp")
  @Test
  void aLaterRequestDoesNotRefuseTheRejectedSubstitute() throws Exception {
    HelpRequest earlier = HelpRequests.lenaAsksForASubstitute(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(earlier, HelpRequests.gramsOf("mascarpone", "200"));
    earlier.reject(
        HelpRequests.HELP_ID,
        "too heavy",
        Clock.fixed(Instant.parse("2026-11-09T19:00:00Z"), ZoneOffset.UTC));
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    mvc.perform(
            post("/help-requests/" + MUSHROOM_SAUCE_REQUEST_ID.value() + "/helps")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.JONAS)
                        .roles("cook", "community")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "answerTitle": "Mascarpone, thinned",
                      "helpProviderType": "COMMUNITY",
                      "answer": {
                        "answerType": "INGREDIENT_SUBSTITUTE",
                        "recipe": "%s",
                        "substitute": [{"ingredient": "%s", "substituteIngredients": [
                          {"name": "mascarpone", "value": 200, "unit": "GRAM"}]}]
                      }
                    }
                    """
                        .formatted(
                            MUSHROOM_SAUCE.value(), CREME_FRAICHE_IN_MUSHROOM_SAUCE.value())))
        .andExpect(status().isCreated());

    assertThat(helpRequests.findById(MUSHROOM_SAUCE_REQUEST_ID).orElseThrow().helps()).hasSize(1);
  }

  @DisplayName(
      "an earlier note on \" Creme Fraiche\" -> shown on a later request about \"creme fraiche\" -- B3-S05 US-CA-02 INV-15 S4 S3 op getHelpRequestById")
  @Test
  void previousNotesMatchNamesWithoutCaseOrOuterSpace() throws Exception {
    HelpRequest earlier =
        HelpRequest.requestHelpWithIngredient(
            new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000025")),
            HelpRequests.LENA,
            "Creme fraiche in tomato soup",
            "The shop has none",
            HelpRequests.TOMATO_SOUP,
            HelpRequests.CREME_FRAICHE,
            " Creme Fraiche",
            "200 g",
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    earlier.cancel(Optional.of("sour cream curdled"), HelpRequests.FRIDAY_18_10);
    helpRequests.save(earlier);
    helpRequests.save(lenaAsksAboutMushroomSauceNextWeek("creme fraiche"));

    String body = read(MUSHROOM_SAUCE_REQUEST_ID.value().toString(), JONAS_READING, 200);

    JsonNode previous = new ObjectMapper().readTree(body).get("previousNotes");
    assertThat(previous).as("previousNotes").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("text").asText()).isEqualTo("sour cream curdled");
  }
}
