package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Operations of tag Help Requests (createHelpRequest, getHelpRequestById) through the real web
 * layer, real resource-server security and the step 8 use cases with contract-checked fakes (no
 * database).
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class HelpRequestsControllerTest {

  static final String ID = HelpRequests.HELP_REQUEST_ID.value().toString();
  static final String LENA_AS_COOK =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();
  static final String LENAS_REQUEST =
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
          .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());

  @Autowired MockMvc mvc;
  @Autowired InMemoryHelpRequestRepository helpRequests;

  @BeforeEach
  void noHelpRequestIsStored() {
    helpRequests.clear();
  }

  @DisplayName(
      "Lena (cook, help:write) posts a PSE request to COMMUNITY -> 201 {helpRequestId: generated id, helpRequestLink: .../help-requests/<id>} matching HelpRequestLink; the stored request has Lena as requester and her title, recipe, step and provider -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void lenaRequestsHelpWithAPreparationStep() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/help-requests")
                    .header(AUTHORIZATION, LENA_AS_COOK)
                    .header("version", "1.0.2")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(LENAS_REQUEST))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.helpRequestId").value(ID))
            .andExpect(jsonPath("$.helpRequestLink").value("http://localhost/help-requests/" + ID))
            .andReturn();

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpRequestLink");
    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.requester()).isEqualTo(HelpRequests.LENA);
    assertThat(stored.title()).isEqualTo("Sauce splits");
    assertThat(stored.recipe()).isEqualTo(HelpRequests.HOLLANDAISE);
    assertThat(stored.howToStep()).isEqualTo(HelpRequests.STEP_4);
    assertThat(stored.askedProviderTypes()).containsExactly(HelpProviderType.COMMUNITY);
  }

  @DisplayName(
      "Lena's request sent with Host evil.example:8443 -> helpRequestLink starts with the public base URL http://localhost -- B3-S01 US-CA-03 OWASP-API8 op createHelpRequest")
  @Test
  void linksIgnoreTheRequestHost() throws Exception {
    mvc.perform(
            post("/help-requests")
                .with(
                    request -> {
                      request.setServerName("evil.example");
                      request.setServerPort(8443);
                      return request;
                    })
                .header("Host", "evil.example:8443")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.helpRequestLink").value("http://localhost/help-requests/" + ID));
  }

  @DisplayName(
      "body over 1 MB (1 MB of padding plus the request) -> 413 PAYLOAD_TOO_LARGE Problem, nothing saved -- B3-S01 US-CA-03 OWASP-API4 ADR0019 op createHelpRequest")
  @Test
  void bodyOverOneMegabyteIsTooLarge() throws Exception {
    String padded =
        "{\"padding\": \""
            + "x".repeat(1024 * 1024)
            + "\","
            + LENAS_REQUEST.substring(LENAS_REQUEST.indexOf('{') + 1);

    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(padded))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "title missing -> 400 VALIDATION_FAILED, nothing saved -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void missingTitleIsRejected() throws Exception {
    String withoutTitle = LENAS_REQUEST.replace("\"title\": \"Sauce splits\",", "");

    expectValidationFailed(withoutTitle);
  }

  private void expectValidationFailed(String body) throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "title missing -> the 400 VALIDATION_FAILED names the violation in detail -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void validationFailureNamesTheViolation() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST.replace("\"title\": \"Sauce splits\",", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value(containsString("title")));
  }

  @DisplayName(
      "type \"COOKIE\" -> 400 VALIDATION_FAILED, nothing saved -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void invalidTypeIsRejected() throws Exception {
    expectValidationFailed(LENAS_REQUEST.replace("\"PREPARATION_STEP_EXPLANATION\"", "\"COOKIE\""));
  }

  @DisplayName(
      "PSE without howToStep -> 400 VALIDATION_FAILED, nothing saved (if/then checked) -- B3-S01 US-CA-03 INV-02 op createHelpRequest")
  @Test
  void preparationStepWithoutHowToStepIsRejected() throws Exception {
    expectValidationFailed(
        LENAS_REQUEST.replace("\"howToStep\": \"%s\",".formatted(HelpRequests.STEP_4.value()), ""));
  }

  @DisplayName(
      "recipe that is not a UUID (\"not-a-uuid\") -> 400 VALIDATION_FAILED, nothing saved -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void recipeThatIsNoUuidIsRejected() throws Exception {
    expectValidationFailed(
        LENAS_REQUEST.replace(HelpRequests.HOLLANDAISE.value().toString(), "not-a-uuid"));
  }

  @DisplayName(
      "PSE with preferredProvider [CHEF] -> 400 VALIDATION_FAILED, nothing saved (schema else branch) -- B3-S01 US-CA-03 INV-01 op createHelpRequest")
  @Test
  void chefForAPreparationStepIsRejected() throws Exception {
    expectValidationFailed(LENAS_REQUEST.replace("[\"COMMUNITY\"]", "[\"CHEF\"]"));
  }

  @DisplayName(
      "no version header -> 400 VALIDATION_FAILED, nothing saved -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void missingVersionHeaderIsRejected() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "empty body -> 400 VALIDATION_FAILED, nothing saved -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void emptyBodyIsRejected() throws Exception {
    expectValidationFailed("");
  }

  @DisplayName(
      "valid INGREDIENT_SUBSTITUTE request -> 501 NOT_IMPLEMENTED, nothing saved (increment 2) -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void ingredientSubstituteIsNotImplementedYet() throws Exception {
    String substituteRequest =
        """
        {
          "title": "No butter at home",
          "type": "INGREDIENT_SUBSTITUTE",
          "description": "What can I use instead of butter?",
          "recipe": "%s",
          "ingredient": "8956b2e5-8d0c-470e-ae9c-6071bd7c5b0d",
          "preferredProvider": ["COMMUNITY"]
        }
        """
            .formatted(HelpRequests.HOLLANDAISE.value());

    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(substituteRequest))
        .andExpect(status().isNotImplemented())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "no token -> 401 UNAUTHENTICATED Problem -- B3-S01 US-CA-03 ADR0012 op createHelpRequest")
  @Test
  void noTokenIsUnauthenticated() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "token for audience recipe-catalog -> 401 UNAUTHENTICATED -- B3-S01 US-CA-03 ADR0012 op createHelpRequest")
  @Test
  void tokenForAnotherAudienceIsUnauthenticated() throws Exception {
    expectUnauthenticated(
        TestTokens.forCook(HelpRequests.LENA)
            .roles("cook")
            .scope("help:write")
            .audience("recipe-catalog")
            .bearer());
  }

  @DisplayName(
      "token from issuer https://other.example -> 401 UNAUTHENTICATED -- B3-S01 US-CA-03 ADR0012 op createHelpRequest")
  @Test
  void tokenFromAnotherIssuerIsUnauthenticated() throws Exception {
    expectUnauthenticated(
        TestTokens.forCook(HelpRequests.LENA)
            .roles("cook")
            .scope("help:write")
            .issuer("https://other.example")
            .bearer());
  }

  private void expectUnauthenticated(String bearer) throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, bearer)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "expired token -> 401 UNAUTHENTICATED -- B3-S01 US-CA-03 ADR0012 op createHelpRequest")
  @Test
  void expiredTokenIsUnauthenticated() throws Exception {
    expectUnauthenticated(
        TestTokens.forCook(HelpRequests.LENA)
            .roles("cook")
            .scope("help:write")
            .expiresAt(Instant.now().minusSeconds(300))
            .bearer());
  }

  @DisplayName(
      "correctly signed token without exp -> 401 UNAUTHENTICATED, nothing saved -- B3-S01 US-CA-03 OWASP-API2 ADR0012 op createHelpRequest")
  @Test
  void tokenWithoutExpiryIsUnauthenticated() throws Exception {
    expectUnauthenticated(
        TestTokens.forCook(HelpRequests.LENA)
            .roles("cook")
            .scope("help:write")
            .withoutExpiry()
            .bearer());
  }

  @DisplayName(
      "token whose sub is not a UUID -> 401 UNAUTHENTICATED -- B3-S01 US-CA-03 ADR0012 op createHelpRequest")
  @Test
  void subjectThatIsNoUuidIsUnauthenticated() throws Exception {
    expectUnauthenticated(TestTokens.forSubject("lena").roles("cook").scope("help:write").bearer());
  }

  @DisplayName(
      "scope help:read only -> 403 INSUFFICIENT_SCOPE, nothing saved -- B3-S01 US-CA-03 ADR0013 op createHelpRequest")
  @Test
  void readScopeCannotRequestHelp() throws Exception {
    expectForbidden(
        TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer(),
        "INSUFFICIENT_SCOPE");
  }

  private void expectForbidden(String bearer, String code) throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, bearer)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(code));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "actor without role cook -> 403 NOT_AUTHORIZED, nothing saved -- B3-S01 US-CA-03 ADR0013 op createHelpRequest")
  @Test
  void actorWithoutCookRoleIsRefused() throws Exception {
    expectForbidden(
        TestTokens.forCook(HelpRequests.LENA).roles("community").scope("help:write").bearer(),
        "NOT_AUTHORIZED");
  }

  @DisplayName(
      "token without a roles claim -> 403 NOT_AUTHORIZED, nothing saved -- B3-S01 US-CA-03 ADR0012 ADR0013 op createHelpRequest")
  @Test
  void tokenWithoutRolesIsRefused() throws Exception {
    expectForbidden(
        TestTokens.forCook(HelpRequests.LENA).withoutRoles().scope("help:write").bearer(),
        "NOT_AUTHORIZED");
  }

  @DisplayName(
      "token with roles cook and admin -> 201; the unknown role grants nothing -- B3-S01 US-CA-03 ADR0013 op createHelpRequest")
  @Test
  void unknownRoleIsIgnored() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook", "admin")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isCreated());
  }

  @DisplayName(
      "use case throws RuntimeException(\"connection refused for user larder\") -> 500 INTERNAL_ERROR, body does not contain the message -- B3-S01 US-CA-03 op createHelpRequest")
  @Test
  void unexpectedErrorHidesItsMessage() throws Exception {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LENAS_REQUEST))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("connection refused"))));
  }
}
