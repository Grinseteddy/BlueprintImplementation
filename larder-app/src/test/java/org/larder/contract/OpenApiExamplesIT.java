package org.larder.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.acceptance.DatabaseReset;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.client.RestClient;

/**
 * The documented OpenAPI examples replayed against the running service (example-based contract
 * check).
 */
@SpringBootTest(
    classes = LarderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class OpenApiExamplesIT {

  @Value("${local.server.port}")
  int port;

  @Autowired JdbcClient jdbc;

  final OpenApiDocument spec = new OpenApiDocument();

  /** Each check sees only what it creates, whatever order the methods run in. */
  @BeforeEach
  void emptyTables() {
    DatabaseReset.emptyAllTables(jdbc);
  }

  private ResponseEntity<String> send(
      HttpMethod method, String path, CookId cook, String roles, String body) {
    RestClient.RequestBodySpec request =
        RestClient.create("http://localhost:" + port)
            .method(method)
            .uri(path)
            .header(
                "Authorization",
                TestTokens.forCook(cook)
                    .roles(roles.split(","))
                    .scope("help:read help:write")
                    .bearer())
            .header("version", "1.0.4");
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return request
        .retrieve()
        .onStatus(HttpStatusCode::isError, (req, res) -> {})
        .toEntity(String.class);
  }

  private void answeredAsSpecified(ResponseEntity<String> response, int status, String schema) {
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(spec.violations(schema, response.getBody()))
        .as("%s against %s", response.getBody(), schema)
        .isEmpty();
  }

  /** Jonas's Help from the spec's provideHelp example on the given request; its id. */
  private String providedFromTheExample(String helpRequestId) throws Exception {
    ResponseEntity<String> provided =
        send(
            HttpMethod.POST,
            "/help-requests/" + helpRequestId + "/helps",
            HelpRequests.JONAS,
            "cook,community",
            spec.requestBodyExample("HelpCreateRequest", "preparationStepExplanation"));
    answeredAsSpecified(provided, 201, "HelpLink");
    String link = new ObjectMapper().readTree(provided.getBody()).get("helpLink").asText();
    return link.substring(link.lastIndexOf('/') + 1);
  }

  /** Lena's request from the spec's createHelpRequest example; its id. */
  private String createdFromTheExample() throws Exception {
    ResponseEntity<String> created =
        send(
            HttpMethod.POST,
            "/help-requests",
            HelpRequests.LENA,
            "cook",
            spec.requestBodyExample("HelpRequestCreateRequest", "preparationStep"));
    answeredAsSpecified(created, 201, "HelpRequestLink");
    return new ObjectMapper().readTree(created.getBody()).get("helpRequestId").asText();
  }

  @DisplayName(
      "D1 createHelpRequest example -> 201 HelpRequestLink; provideHelp example on that request -> 201 HelpLink; getHelpRequestById -> 200 HelpRequest; each body valid against the spec -- B3-S01,B3-S02 US-CA-03 op createHelpRequest,provideHelp,getHelpRequestById")
  @Test
  void documentedExamplesAreAnsweredAsSpecified() throws Exception {
    String id = createdFromTheExample();
    providedFromTheExample(id);

    answeredAsSpecified(
        send(HttpMethod.GET, "/help-requests/" + id, HelpRequests.JONAS, "cook,community", null),
        200,
        "HelpRequest");
  }

  @DisplayName(
      "D2 cancelHelpRequest example withNote on a created request -> 200 HelpRequestLink, valid against the spec -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void cancellationExampleIsAnsweredAsSpecified() throws Exception {
    String id = createdFromTheExample();

    answeredAsSpecified(
        send(
            HttpMethod.POST,
            "/help-requests/" + id + "/cancellation",
            HelpRequests.LENA,
            "cook",
            spec.requestBodyExample("CancellationRequest", "withNote")),
        200,
        "HelpRequestLink");
  }

  @DisplayName(
      "D3 acceptHelp on a provided Help -> 200 HelpRequestLink, valid against the spec -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void acceptanceIsAnsweredAsSpecified() throws Exception {
    String id = createdFromTheExample();
    String help = providedFromTheExample(id);

    answeredAsSpecified(
        send(
            HttpMethod.POST,
            "/help-requests/" + id + "/helps/" + help + "/acceptance",
            HelpRequests.LENA,
            "cook",
            null),
        200,
        "HelpRequestLink");
  }

  @DisplayName(
      "D4 rejectHelp example warmWater on a provided Help -> 200 HelpRequestLink, valid against the spec -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void rejectionExampleIsAnsweredAsSpecified() throws Exception {
    String id = createdFromTheExample();
    String help = providedFromTheExample(id);

    answeredAsSpecified(
        send(
            HttpMethod.POST,
            "/help-requests/" + id + "/helps/" + help + "/rejection",
            HelpRequests.LENA,
            "cook",
            spec.requestBodyExample("RejectionRequest", "warmWater")),
        200,
        "HelpRequestLink");
  }

  @DisplayName(
      "D5 listHelpRequests, listHelpsOfHelpRequest, getHelpOfHelpRequest, listHelps -> 200, bodies valid against HelpRequestPage, HelpPage, Help, HelpPage -- B3-S03,B3-S04,B3-S05 US-CA-03 op listHelpRequests,listHelpsOfHelpRequest,getHelpOfHelpRequest,listHelps")
  @Test
  void readOperationsAreAnsweredAsSpecified() throws Exception {
    String id = createdFromTheExample();
    String help = providedFromTheExample(id);

    answeredAsSpecified(
        send(HttpMethod.GET, "/help-requests", HelpRequests.JONAS, "cook,community", null),
        200,
        "HelpRequestPage");
    answeredAsSpecified(
        send(HttpMethod.GET, "/help-requests/" + id + "/helps", HelpRequests.LENA, "cook", null),
        200,
        "HelpPage");
    answeredAsSpecified(
        send(
            HttpMethod.GET,
            "/help-requests/" + id + "/helps/" + help,
            HelpRequests.LENA,
            "cook",
            null),
        200,
        "Help");
    answeredAsSpecified(
        send(HttpMethod.GET, "/helps", HelpRequests.JONAS, "cook,community", null),
        200,
        "HelpPage");
  }

  @DisplayName(
      "D6 createHelpRequest example ingredientSubstitute -> 201 HelpRequestLink; provideHelp example substitutes on that request -> 201 HelpLink; getHelpRequestById -> 200 HelpRequest, getHelpOfHelpRequest -> 200 Help; each body valid against the spec -- B2-S01,B2-S02 US-CA-02 INV-11 INV-12 op createHelpRequest,provideHelp,getHelpRequestById,getHelpOfHelpRequest (step 4 r22)")
  @Test
  void ingredientExamplesAreAnsweredAsSpecified() throws Exception {
    ResponseEntity<String> created =
        send(
            HttpMethod.POST,
            "/help-requests",
            HelpRequests.LENA,
            "cook",
            spec.requestBodyExample("HelpRequestCreateRequest", "ingredientSubstitute"));
    answeredAsSpecified(created, 201, "HelpRequestLink");
    String id = new ObjectMapper().readTree(created.getBody()).get("helpRequestId").asText();
    ResponseEntity<String> provided =
        send(
            HttpMethod.POST,
            "/help-requests/" + id + "/helps",
            HelpRequests.JONAS,
            "cook,community",
            spec.requestBodyExample("HelpCreateRequest", "substitutes"));
    answeredAsSpecified(provided, 201, "HelpLink");
    String link = new ObjectMapper().readTree(provided.getBody()).get("helpLink").asText();

    answeredAsSpecified(
        send(HttpMethod.GET, "/help-requests/" + id, HelpRequests.JONAS, "cook,community", null),
        200,
        "HelpRequest");
    answeredAsSpecified(
        send(
            HttpMethod.GET,
            "/help-requests/" + id + "/helps/" + link.substring(link.lastIndexOf('/') + 1),
            HelpRequests.LENA,
            "cook",
            null),
        200,
        "Help");
  }
}
