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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.in.rest.SpecSchemas;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
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
 * The Help reads (listHelpsOfHelpRequest, getHelpOfHelpRequest, listHelps) through the real web
 * layer and the JDBC query adapter on PostgreSQL: read from the aggregate's tables, never the
 * aggregate (ADR0016, read-your-writes QS-07).
 */
@SpringBootTest(classes = PersistenceTestApplication.class)
@AutoConfigureMockMvc
@Import({PostgresTestContainer.class, TestSigningKeys.class})
class HelpQueryIT {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  static final String LENA_READING =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer();

  /** Grandma answers at Friday 18:10 CET, Jonas at 18:20 (UUIDv7 ids of those times). */
  static final HelpId GRANDMAS_HELP =
      new HelpId(UUID.fromString("01a1b1d6-b240-7000-8000-000000000001"));

  static final HelpId JONAS_HELP =
      new HelpId(UUID.fromString("01a1b1df-da00-7000-8000-000000000002"));

  static final Clock FRIDAY_18_20 =
      Clock.fixed(Instant.parse("2026-11-06T17:20:00Z"), ZoneOffset.UTC);

  static final Clock FRIDAY_18_30 =
      Clock.fixed(Instant.parse("2026-11-06T17:30:00Z"), ZoneOffset.UTC);

  static final HelpRequestId MIAS_REQUEST =
      new HelpRequestId(UUID.fromString("01a1b1cd-8a80-7000-8000-000000000003"));

  static final HelpId JONAS_HELP_FOR_MIA =
      new HelpId(UUID.fromString("01a1b1df-da00-7000-8000-000000000004"));

  @Autowired HelpRequestRepository helpRequests;

  private String helps(String query, int expectedStatus) throws Exception {
    return mvc.perform(
            get("/helps" + query).header(AUTHORIZATION, LENA_READING).header("version", "1.0.8"))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String help(String helpRequestId, String helpId, int expectedStatus) throws Exception {
    return mvc.perform(
            get("/help-requests/" + helpRequestId + "/helps/" + helpId)
                .header(AUTHORIZATION, LENA_READING)
                .header("version", "1.0.8"))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** Lena asks Grandma and the Community; Grandma answers at 18:10, Jonas at 18:20. */
  private static HelpRequest grandmaThenJonasAnswer() {
    HelpRequest lenas =
        HelpRequests.lenaAsksAboutStep4(
            HelpProviderType.GRANDMA_AVATAR, HelpProviderType.COMMUNITY);
    lenas.provideHelp(
        GRANDMAS_HELP,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);
    lenas.provideHelp(
        JONAS_HELP,
        HelpProvider.community(HelpRequests.JONAS),
        "Warm the bowl",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_18_20);
    return lenas;
  }

  private String helpsOf(String helpRequestId, String query, int expectedStatus) throws Exception {
    return mvc.perform(
            get("/help-requests/" + helpRequestId + "/helps" + query)
                .header(AUTHORIZATION, LENA_READING)
                .header("version", "1.0.8"))
        .andExpect(status().is(expectedStatus))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @BeforeEach
  void emptyTables() {
    jdbc.sql("delete from processed_message").update();
    jdbc.sql("delete from outbox").update();
    jdbc.sql("delete from help_request_note").update();
    jdbc.sql("delete from help").update();
    jdbc.sql("delete from asked_provider").update();
    jdbc.sql("delete from help_request").update();
  }

  @DisplayName(
      "H1 Grandma's Help, then Jonas's, on Lena's request -> 200 items in creation order with helpId, helpRequest, helpRequester LENA, answerTitle, helpProviderType, answer, isAccepted false; helpProvider JONAS on Jonas's only; each matches the Help schema -- B3-S02,B3-S05 US-CA-03 op listHelpsOfHelpRequest")
  @Test
  void helpsOfARequestInCreationOrder() throws Exception {
    HelpRequest lenas =
        HelpRequests.lenaAsksAboutStep4(
            HelpProviderType.GRANDMA_AVATAR, HelpProviderType.COMMUNITY);
    lenas.provideHelp(
        GRANDMAS_HELP,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);
    lenas.provideHelp(
        JONAS_HELP,
        HelpProvider.community(HelpRequests.JONAS),
        "Warm the bowl",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_18_20);
    helpRequests.save(lenas);

    String body = helpsOf(HelpRequests.HELP_REQUEST_ID.value().toString(), "", 200);

    SpecSchemas.assertMatches(body, "HelpPage");
    JsonNode items = new ObjectMapper().readTree(body).get("items");
    assertThat(items.findValuesAsText("helpId"))
        .containsExactly(GRANDMAS_HELP.value().toString(), JONAS_HELP.value().toString());
    for (JsonNode help : items) {
      SpecSchemas.assertMatches(help.toString(), "Help");
      assertThat(help.get("helpRequest").asText())
          .isEqualTo(HelpRequests.HELP_REQUEST_ID.value().toString());
      assertThat(help.get("helpRequester").asText())
          .isEqualTo(HelpRequests.LENA.value().toString());
      assertThat(help.get("isAccepted").asBoolean()).isFalse();
    }
    JsonNode grandmas = items.get(0);
    assertThat(grandmas.get("answerTitle").asText()).isEqualTo("Butter in a thin stream");
    assertThat(grandmas.get("helpProviderType").asText()).isEqualTo("GRANDMA_AVATAR");
    assertThat(grandmas.get("helpProvider")).as("Grandma's helpProvider").isNull();
    assertThat(grandmas.path("answer").path("answerType").asText())
        .isEqualTo("PREPARATION_STEP_EXPLANATION");
    assertThat(grandmas.path("answer").path("description").asText())
        .isEqualTo("Add the butter in a thin stream");
    assertThat(grandmas.path("answer").path("images")).hasSize(3);
    JsonNode jonass = items.get(1);
    assertThat(jonass.get("answerTitle").asText()).isEqualTo("Warm the bowl");
    assertThat(jonass.get("helpProviderType").asText()).isEqualTo("COMMUNITY");
    assertThat(jonass.path("helpProvider").asText())
        .isEqualTo(HelpRequests.JONAS.value().toString());
  }

  @DisplayName(
      "H2 Lena accepted Jonas's Help -> isAccepted true on Jonas's, false on Grandma's -- B3-S04 US-CA-03 INV-05 op listHelpsOfHelpRequest")
  @Test
  void acceptedHelpIsMarked() throws Exception {
    HelpRequest lenas = grandmaThenJonasAnswer();
    lenas.accept(JONAS_HELP, FRIDAY_18_30);
    helpRequests.save(lenas);

    JsonNode items =
        new ObjectMapper()
            .readTree(helpsOf(HelpRequests.HELP_REQUEST_ID.value().toString(), "", 200))
            .get("items");

    assertThat(items.findValuesAsText("helpId"))
        .containsExactly(GRANDMAS_HELP.value().toString(), JONAS_HELP.value().toString());
    assertThat(items.get(0).get("isAccepted").asBoolean()).as("Grandma's isAccepted").isFalse();
    assertThat(items.get(1).get("isAccepted").asBoolean()).as("Jonas's isAccepted").isTrue();
  }

  @DisplayName(
      "H3 Lena rejected Grandma's Help with 'warm water didn't help' -> rejectionNote 'warm water didn't help' on Grandma's; absent on Jonas's -- B3-S05 US-CA-03 INV-06 R5.E1 op listHelpsOfHelpRequest rejectionNote")
  @Test
  void rejectedHelpCarriesTheNote() throws Exception {
    HelpRequest lenas = grandmaThenJonasAnswer();
    lenas.reject(GRANDMAS_HELP, "warm water didn't help", FRIDAY_18_30);
    helpRequests.save(lenas);

    String body = helpsOf(HelpRequests.HELP_REQUEST_ID.value().toString(), "", 200);

    SpecSchemas.assertMatches(body, "HelpPage");
    JsonNode items = new ObjectMapper().readTree(body).get("items");
    assertThat(items.get(0).path("rejectionNote").asText())
        .as("Grandma's rejectionNote")
        .isEqualTo("warm water didn't help");
    assertThat(items.get(1).get("rejectionNote")).as("Jonas's rejectionNote").isNull();
  }

  @DisplayName(
      "H4 unknown help request -> 404 NOT_FOUND Problem; limit=1 of two -> one item and a nextCursor, following it -> the second; cursor 'abc' -> 400 VALIDATION_FAILED -- B3-S02 US-CA-03 ADR0016 L6 op listHelpsOfHelpRequest")
  @Test
  void helpsOfARequestAreFoundAndPaged() throws Exception {
    helpRequests.save(grandmaThenJonasAnswer());
    String id = HelpRequests.HELP_REQUEST_ID.value().toString();

    String unknown = helpsOf("01a1b1cd-8a80-7000-8000-0000000000ff", "", 404);
    JsonNode first = new ObjectMapper().readTree(helpsOf(id, "?limit=1", 200));
    JsonNode second =
        new ObjectMapper()
            .readTree(helpsOf(id, "?limit=1&cursor=" + first.path("nextCursor").asText(), 200));
    String foreign = helpsOf(id, "?cursor=abc", 400);

    assertThat(new ObjectMapper().readTree(unknown).path("code").asText()).isEqualTo("NOT_FOUND");
    assertThat(first.get("items").findValuesAsText("helpId"))
        .as("page 1")
        .containsExactly(GRANDMAS_HELP.value().toString());
    assertThat(first.path("nextCursor").asText()).as("nextCursor of page 1").isNotEmpty();
    assertThat(second.get("items").findValuesAsText("helpId"))
        .as("page 2")
        .containsExactly(JONAS_HELP.value().toString());
    assertThat(second.get("nextCursor")).as("nextCursor of page 2").isNull();
    assertThat(new ObjectMapper().readTree(foreign).path("code").asText())
        .isEqualTo("VALIDATION_FAILED");
  }

  @DisplayName(
      "H5 getHelpOfHelpRequest -> 200 with that Help; unknown helpId -> 404 NOT_FOUND; a helpId of another request -> 404 NOT_FOUND -- B3-S04 US-CA-03 INV-05 op getHelpOfHelpRequest")
  @Test
  void oneHelpOfARequest() throws Exception {
    helpRequests.save(grandmaThenJonasAnswer());
    HelpRequest mias =
        HelpRequest.requestHelpWithPreparationStep(
            MIAS_REQUEST,
            HelpRequests.MIA,
            "Splitting sauce",
            "It splits",
            HelpRequests.HOLLANDAISE,
            HelpRequests.STEP_4,
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    HelpRequests.communityMemberExplainsStep4(mias, JONAS_HELP_FOR_MIA, HelpRequests.JONAS);
    helpRequests.save(mias);
    String lenas = HelpRequests.HELP_REQUEST_ID.value().toString();

    String found = help(lenas, JONAS_HELP.value().toString(), 200);
    String unknown = help(lenas, "01a1b1cd-8a80-7000-8000-0000000000ee", 404);
    String others = help(lenas, JONAS_HELP_FOR_MIA.value().toString(), 404);

    SpecSchemas.assertMatches(found, "Help");
    JsonNode jonass = new ObjectMapper().readTree(found);
    assertThat(jonass.get("helpId").asText()).isEqualTo(JONAS_HELP.value().toString());
    assertThat(jonass.get("helpRequest").asText()).isEqualTo(lenas);
    assertThat(jonass.get("answerTitle").asText()).isEqualTo("Warm the bowl");
    assertThat(new ObjectMapper().readTree(unknown).path("code").asText())
        .as("unknown helpId")
        .isEqualTo("NOT_FOUND");
    assertThat(new ObjectMapper().readTree(others).path("code").asText())
        .as("helpId of another request")
        .isEqualTo("NOT_FOUND");
  }

  @DisplayName(
      "H6 helpProvider=Jonas -> only Jonas's Helps across requests, oldest first; answer type filter; limit=1 -> nextCursor and the rest on the next page -- B3-S02 US-CA-03 ADR0016 L6 op listHelps")
  @Test
  void helpsOfAProviderAcrossRequests() throws Exception {
    helpRequests.save(grandmaThenJonasAnswer());
    HelpRequest mias =
        HelpRequest.requestHelpWithPreparationStep(
            MIAS_REQUEST,
            HelpRequests.MIA,
            "Splitting sauce",
            "It splits",
            HelpRequests.HOLLANDAISE,
            HelpRequests.STEP_4,
            Set.of(HelpProviderType.COMMUNITY),
            HelpRequests.FRIDAY_18_00);
    HelpRequests.communityMemberExplainsStep4(mias, JONAS_HELP_FOR_MIA, HelpRequests.JONAS);
    helpRequests.save(mias);

    String byJonas = helps("?helpProvider=" + HelpRequests.JONAS.value(), 200);
    String byType = helps("?type=PREPARATION_STEP_EXPLANATION", 200);
    JsonNode first =
        new ObjectMapper()
            .readTree(helps("?helpProvider=" + HelpRequests.JONAS.value() + "&limit=1", 200));
    JsonNode second =
        new ObjectMapper()
            .readTree(
                helps(
                    "?helpProvider="
                        + HelpRequests.JONAS.value()
                        + "&limit=1&cursor="
                        + first.path("nextCursor").asText(),
                    200));

    SpecSchemas.assertMatches(byJonas, "HelpPage");
    assertThat(new ObjectMapper().readTree(byJonas).get("items").findValuesAsText("helpId"))
        .as("helpProvider=Jonas")
        .containsExactly(JONAS_HELP.value().toString(), JONAS_HELP_FOR_MIA.value().toString());
    assertThat(new ObjectMapper().readTree(byType).get("items").findValuesAsText("helpId"))
        .as("type=PREPARATION_STEP_EXPLANATION")
        .containsExactly(
            GRANDMAS_HELP.value().toString(),
            JONAS_HELP.value().toString(),
            JONAS_HELP_FOR_MIA.value().toString());
    assertThat(first.get("items").findValuesAsText("helpId"))
        .as("page 1")
        .containsExactly(JONAS_HELP.value().toString());
    assertThat(second.get("items").findValuesAsText("helpId"))
        .as("page 2")
        .containsExactly(JONAS_HELP_FOR_MIA.value().toString());
    assertThat(second.get("nextCursor")).as("nextCursor of page 2").isNull();
  }

  @DisplayName(
      "H7 no token -> 401 UNAUTHENTICATED; token with help:write only -> 403 INSUFFICIENT_SCOPE; for listHelpsOfHelpRequest, getHelpOfHelpRequest and listHelps -- B3-S02 US-CA-03 ADR0012 ADR0013 op listHelpsOfHelpRequest,op getHelpOfHelpRequest,op listHelps")
  @Test
  void helpReadsNeedTheReadScope() throws Exception {
    String writeOnly =
        TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();
    String lenas = "/help-requests/" + HelpRequests.HELP_REQUEST_ID.value();
    for (String path :
        List.of(lenas + "/helps", lenas + "/helps/" + JONAS_HELP.value(), "/helps")) {
      mvc.perform(get(path).header("version", "1.0.8"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
      mvc.perform(get(path).header(AUTHORIZATION, writeOnly).header("version", "1.0.8"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
    }
  }

  @DisplayName(
      "G3 Lena rejects Grandma's Help over HTTP, then GETs the request's helps at once -> rejectionNote shown (read-your-writes) -- B3-S05 US-CA-03 QS-07 ADR0016 op rejectHelp,op listHelpsOfHelpRequest")
  @Test
  void rejectionIsReadAtOnce() throws Exception {
    helpRequests.save(grandmaThenJonasAnswer());
    String lenas = HelpRequests.HELP_REQUEST_ID.value().toString();

    mvc.perform(
            post("/help-requests/" + lenas + "/helps/" + GRANDMAS_HELP.value() + "/rejection")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.8")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"warm water didn't help\"}"))
        .andExpect(status().isOk());
    JsonNode items = new ObjectMapper().readTree(helpsOf(lenas, "", 200)).get("items");

    assertThat(items.get(0).path("rejectionNote").asText())
        .as("Grandma's rejectionNote right after the rejection")
        .isEqualTo("warm water didn't help");
  }

  @DisplayName(
      "H8 type=INGREDIENT_SUBSTITUTE, which the spec allows but no Help has yet -> 200 with no items, not 500 -- B3-S02 US-CA-03 op listHelps (step 11 r1 from step 12, Schemathesis)")
  @Test
  void typeNoHelpHasYetListsNothing() throws Exception {
    helpRequests.save(grandmaThenJonasAnswer());

    String body = helps("?type=INGREDIENT_SUBSTITUTE", 200);

    SpecSchemas.assertMatches(body, "HelpPage");
    assertThat(new ObjectMapper().readTree(body).get("items")).isEmpty();
  }
}
