package org.larder.cookingassistance.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.adapter.in.rest.SpecSchemas;
import org.larder.cookingassistance.adapter.out.persistence.PersistenceTestApplication;
import org.larder.cookingassistance.adapter.out.persistence.PostgresTestContainer;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
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
            "status");
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
}
