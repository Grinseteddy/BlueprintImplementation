package org.larder.cookingassistance.adapter.in.rest;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
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
}
