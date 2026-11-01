package org.larder.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.LarderApplication;
import org.larder.acceptance.AcceptanceInfrastructure;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** The documented OpenAPI examples replayed against the running service (example-based contract check). */
@SpringBootTest(classes = LarderApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({AcceptanceInfrastructure.class, TestSigningKeys.class})
class OpenApiExamplesIT {

  @Value("${local.server.port}")
  int port;

  final OpenApiDocument spec = new OpenApiDocument();

  private ResponseEntity<String> send(HttpMethod method, String path, CookId cook, String roles, String body) {
    RestClient.RequestBodySpec request =
        RestClient.create("http://localhost:" + port)
            .method(method)
            .uri(path)
            .header("Authorization", TestTokens.forCook(cook).roles(roles.split(",")).scope("help:read help:write").bearer())
            .header("version", "1.0.4");
    if (body != null) {
      request = request.contentType(MediaType.APPLICATION_JSON).body(body);
    }
    return request.retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {}).toEntity(String.class);
  }

  private void answeredAsSpecified(ResponseEntity<String> response, int status, String schema) {
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(spec.violations(schema, response.getBody())).as("%s against %s", response.getBody(), schema).isEmpty();
  }

  @DisplayName("D1 createHelpRequest example -> 201 HelpRequestLink; provideHelp example on that request -> 201 HelpLink; getHelpRequestById -> 200 HelpRequest; each body valid against the spec -- B3-S01,B3-S02 US-CA-03 op createHelpRequest,provideHelp,getHelpRequestById")
  @Test
  void documentedExamplesAreAnsweredAsSpecified() throws Exception {
    ResponseEntity<String> created =
        send(HttpMethod.POST, "/help-requests", HelpRequests.LENA, "cook",
            spec.requestBodyExample("HelpRequestCreateRequest", "preparationStep"));
    answeredAsSpecified(created, 201, "HelpRequestLink");
    String id = new ObjectMapper().readTree(created.getBody()).get("helpRequestId").asText();

    answeredAsSpecified(
        send(HttpMethod.POST, "/help-requests/" + id + "/helps", HelpRequests.JONAS, "cook,community",
            spec.requestBodyExample("HelpCreateRequest", "preparationStepExplanation")),
        201, "HelpLink");
    answeredAsSpecified(send(HttpMethod.GET, "/help-requests/" + id, HelpRequests.JONAS, "cook,community", null), 200, "HelpRequest");
  }
}
