package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * What the whole API shares: undeclared paths, methods and content types, unparsable bodies ->
 * Problem.
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class EdgeTest {

  static final String LENA =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read help:write").bearer();

  @Autowired MockMvc mvc;
  @Autowired InMemoryHelpRequestRepository helpRequests;

  /** The fake is shared by the cached context: "nothing saved" holds whatever ran before. */
  @BeforeEach
  void noHelpRequestIsStored() {
    helpRequests.clear();
  }

  @DisplayName("GET /recipes -> 404 NOT_FOUND Problem -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredPathIsNotFound() throws Exception {
    mvc.perform(get("/recipes").header(AUTHORIZATION, LENA).header("version", "1.0.3"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "DELETE /help-requests -> 405 METHOD_NOT_ALLOWED Problem with Allow: GET, POST -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredMethodIsNotAllowed() throws Exception {
    mvc.perform(delete("/help-requests").header(AUTHORIZATION, LENA).header("version", "1.0.3"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", allOf(containsString("GET"), containsString("POST"))))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @DisplayName(
      "POST /help-requests as text/plain -> 415 UNSUPPORTED_MEDIA_TYPE Problem -- B3-S01 US-CA-03 edge")
  @Test
  void undeclaredContentTypeIsUnsupported() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA)
                .header("version", "1.0.3")
                .contentType(MediaType.TEXT_PLAIN)
                .content("help me"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @DisplayName(
      "POST /help-requests with malformed JSON -> 400 VALIDATION_FAILED Problem -- B3-S01 US-CA-03 edge")
  @Test
  void malformedJsonIsABadRequest() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @DisplayName(
      "POST /help-requests with U+0000 in the title -> 400 VALIDATION_FAILED Problem, nothing saved; never a 500 from the database -- B3-S01 US-CA-03 edge (step 9 r1 from step 12, Schemathesis)")
  @Test
  void nulCharacterIsABadRequest() throws Exception {
    mvc.perform(
            post("/help-requests")
                .header(AUTHORIZATION, LENA)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title":"Sauce\\u0000splits","type":"PREPARATION_STEP_EXPLANATION",
                     "description":"Step 4 keeps splitting","recipe":"%s","howToStep":"%s",
                     "preferredProvider":["COMMUNITY"]}"""
                        .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value())))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID)).isEmpty();
  }

  @DisplayName(
      "GET /help-requests/{id} accepting only application/xml -> 406 NOT_ACCEPTABLE Problem -- B3-S01 US-CA-03 edge")
  @Test
  void unacceptableMediaTypeIsNotAcceptable() throws Exception {
    mvc.perform(
            get("/help-requests/{id}", HelpRequests.HELP_REQUEST_ID.value())
                .header(AUTHORIZATION, LENA)
                .header("version", "1.0.3")
                .accept(MediaType.APPLICATION_XML))
        .andExpect(status().isNotAcceptable())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));
  }

  @DisplayName(
      "HEAD /help-requests/{id} with scope help:write only -> 403 INSUFFICIENT_SCOPE -- B3-S01 US-CA-03 OWASP-API5 ADR0013 op getHelpRequestById")
  @Test
  void headNeedsTheReadScope() throws Exception {
    mvc.perform(
            head("/help-requests/{id}", HelpRequests.HELP_REQUEST_ID.value())
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook")
                        .scope("help:write")
                        .bearer())
                .header("version", "1.0.3"))
        .andExpect(status().isForbidden());
  }

  @DisplayName(
      "OPTIONS /help-requests/{id} -> 405 METHOD_NOT_ALLOWED Problem with Allow: GET, never 500 -- B3-S01 US-CA-03 OWASP-A10 edge")
  @Test
  void optionsIsNotAllowed() throws Exception {
    mvc.perform(
            options("/help-requests/{id}", HelpRequests.HELP_REQUEST_ID.value())
                .header(AUTHORIZATION, LENA)
                .header("version", "1.0.3"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().string("Allow", containsString("GET")))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @DisplayName(
      "GET /error -> 404 NOT_FOUND Problem like any path the spec does not declare, never 500 -- B3-S01 US-CA-03 OWASP-A10 edge")
  @Test
  void errorPathIsNotFound() throws Exception {
    mvc.perform(get("/error").header(AUTHORIZATION, LENA).header("version", "1.0.3"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  /** Every operation of the spec with its method, a concrete path and the scope it requires. */
  static Stream<String[]> specOperations() throws Exception {
    JsonNode spec;
    try (InputStream in =
        EdgeTest.class
            .getClassLoader()
            .getResourceAsStream("api/cooking-assistance.openapi.yaml")) {
      spec = new ObjectMapper(new YAMLFactory()).readTree(in);
    }
    String someId = UUID.randomUUID().toString();
    List<String[]> operations = new ArrayList<>();
    spec.get("paths")
        .fields()
        .forEachRemaining(
            path ->
                path.getValue()
                    .fields()
                    .forEachRemaining(
                        operation -> {
                          JsonNode security =
                              operation.getValue().has("security")
                                  ? operation.getValue().get("security")
                                  : spec.get("security");
                          operations.add(
                              new String[] {
                                operation.getKey().toUpperCase(java.util.Locale.ROOT),
                                path.getKey().replaceAll("\\{[^}]+}", someId),
                                security.get(0).get("oAuth2").get(0).asText(),
                                operation.getValue().get("operationId").asText()
                              });
                        }));
    return operations.stream();
  }

  @DisplayName(
      "every spec operation called with a token lacking its scope -> 403 INSUFFICIENT_SCOPE -- B3-S01,B3-S02 US-CA-03 OWASP-API5 ADR0013 (D-07 gate)")
  @ParameterizedTest(name = "{3}: {0} {1} without {2}")
  @MethodSource("specOperations")
  void everyOperationRequiresItsScope(
      String method, String path, String requiredScope, String operationId) throws Exception {
    String otherScope = requiredScope.equals("help:read") ? "help:write" : "help:read";
    mvc.perform(
            request(HttpMethod.valueOf(method), path)
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA)
                        .roles("cook", "community")
                        .scope(otherScope)
                        .bearer())
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
  }

  @DisplayName(
      "L1 a request with an invalid token -> 401 and one WARN line with method, path, status and code, never the token -- B3-S01 US-CA-03 OWASP-A09 QS-14 SEC-06")
  @Test
  void refusedAuthenticationIsLogged() throws Exception {
    List<ILoggingEvent> lines =
        securityLogLines(
            () ->
                mvc.perform(
                        post("/help-requests")
                            .header(AUTHORIZATION, "Bearer secret-token-value")
                            .header("version", "1.0.3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized()));

    assertThat(lines)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getLevel()).isEqualTo(Level.WARN);
              assertThat(line.getFormattedMessage())
                  .contains("POST", "/help-requests", "401", "UNAUTHENTICATED")
                  .doesNotContain("secret-token-value");
            });
  }

  @DisplayName(
      "L2 a token without the operation's scope -> 403 and one WARN line with method, path, status and code, never the token or its subject (ADR0019: no personal data) -- B3-S01 US-CA-03 OWASP-A09 QS-14 SEC-06")
  @Test
  void refusedScopeIsLogged() throws Exception {
    String readOnly =
        TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer();

    List<ILoggingEvent> lines =
        securityLogLines(
            () ->
                mvc.perform(
                        post("/help-requests")
                            .header(AUTHORIZATION, readOnly)
                            .header("version", "1.0.3")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden()));

    assertThat(lines)
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.getLevel()).isEqualTo(Level.WARN);
              assertThat(line.getFormattedMessage())
                  .contains("POST", "/help-requests", "403", "INSUFFICIENT_SCOPE")
                  .doesNotContain(
                      readOnly.substring("Bearer ".length()), HelpRequests.LENA.value().toString());
            });
  }

  @DisplayName(
      "W1 /.well-known/oauth-protected-resource with Host evil.example -> resource is the configured public base URL, token binding is not claimed -- B3-S01 US-CA-03 OWASP-API8 OWASP-API9 SEC-15")
  @Test
  void resourceMetadataNeverEchoesTheHost() throws Exception {
    mvc.perform(
            get("/.well-known/oauth-protected-resource")
                .with(
                    request -> {
                      request.setServerName("evil.example");
                      return request;
                    }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resource").value("http://localhost"))
        .andExpect(jsonPath("$.tls_client_certificate_bound_access_tokens").value(false));
  }

  @DisplayName(
      "W2 a 401 for a request with Host evil.example -> WWW-Authenticate never names that host -- B3-S01 US-CA-03 OWASP-API8 SEC-15")
  @Test
  void bearerChallengeNeverEchoesTheHost() throws Exception {
    String challenge =
        mvc.perform(
                get("/help-requests")
                    .header("version", "1.0.3")
                    .with(
                        request -> {
                          request.setServerName("evil.example");
                          return request;
                        }))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getHeader("WWW-Authenticate");

    assertThat(challenge)
        .isEqualTo(
            "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource\"");
  }

  @DisplayName(
      "L7 a 6000-character path, refused with 401 and with 403 -> the WARN line holds only the path's first 200 characters: a sender cannot size the security log -- B3-S01 US-CA-03 OWASP-A09 SEC-19")
  @Test
  void aLongPathCannotSizeTheSecurityLog() throws Exception {
    String path = "/help-requests/" + "x".repeat(6000);
    String readOnly =
        TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer();

    List<ILoggingEvent> lines =
        securityLogLines(
            () -> {
              mvc.perform(post(path).header("version", "1.0.3"))
                  .andExpect(status().isUnauthorized());
              mvc.perform(post(path).header(AUTHORIZATION, readOnly).header("version", "1.0.3"))
                  .andExpect(status().isForbidden());
            });

    assertThat(lines)
        .hasSize(2)
        .allSatisfy(
            line ->
                assertThat(line.getFormattedMessage())
                    .contains("/help-requests/" + "x".repeat(185))
                    .doesNotContain("x".repeat(186)));
  }

  private interface Request {
    void perform() throws Exception;
  }

  /** The security adapter's log lines while {@code request} runs. */
  private static List<ILoggingEvent> securityLogLines(Request request) throws Exception {
    ListAppender<ILoggingEvent> lines = new ListAppender<>();
    lines.start();
    Logger securityLog = (Logger) LoggerFactory.getLogger(SecurityConfiguration.class);
    securityLog.addAppender(lines);
    try {
      request.perform();
    } finally {
      securityLog.detachAppender(lines);
    }
    return lines.list;
  }
}
