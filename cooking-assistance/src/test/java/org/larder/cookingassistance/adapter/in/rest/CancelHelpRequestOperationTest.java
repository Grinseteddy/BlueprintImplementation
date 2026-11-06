package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestCancelled;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.larder.cookingassistance.fixtures.TestSigningKeys;
import org.larder.cookingassistance.fixtures.TestTokens;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Operation cancelHelpRequest (tag Help Requests) through the real web layer, real resource-server
 * security and the step 8 use case with the contract-checked fake (no database).
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class CancelHelpRequestOperationTest {

  static final String REQUEST_ID = HelpRequests.HELP_REQUEST_ID.value().toString();
  static final String CANCELLATION_PATH = "/help-requests/" + REQUEST_ID + "/cancellation";
  static final String LENA_AS_COOK =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();

  @Autowired MockMvc mvc;
  @Autowired InMemoryHelpRequestRepository helpRequests;

  @BeforeEach
  void lenasRequestToGrandmaIsStored() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));
  }

  @DisplayName(
      "X1 Lena cancels her OPEN request with note 'an ice cube saved it' -> 200 {helpRequestLink: .../help-requests/<id>} matching HelpRequestLink; stored CANCELLED with the note -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void lenaCancelsHerOpenRequest() throws Exception {
    MvcResult result =
        mvc.perform(
                post(CANCELLATION_PATH)
                    .header(AUTHORIZATION, LENA_AS_COOK)
                    .header("version", "1.0.7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"note\": \"an ice cube saved it\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.helpRequestId").value(REQUEST_ID))
            .andExpect(
                jsonPath("$.helpRequestLink").value("http://localhost/help-requests/" + REQUEST_ID))
            .andReturn();

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpRequestLink");
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRequestCancelled.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class,
            event -> assertThat(event.note()).contains("an ice cube saved it"));
  }

  @DisplayName(
      "X2 Lena cancels without a body -> 200; stored CANCELLED without a note -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void lenaCancelsWithoutABody() throws Exception {
    mvc.perform(
            post(CANCELLATION_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isOk());

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.CANCELLED);
    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRequestCancelled.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRequestCancelled.class, event -> assertThat(event.note()).isEmpty());
  }

  @DisplayName(
      "X3 note of 2001 characters -> 400 VALIDATION_FAILED, request stays OPEN (schema maxLength) -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void tooLongNoteIsRefused() throws Exception {
    mvc.perform(
            post(CANCELLATION_PATH)
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\": \"%s\"}".formatted("x".repeat(2001))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
  }

  @DisplayName("X4 unknown help request -> 404 NOT_FOUND -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void unknownRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/00000000-0000-7000-8000-000000000009/cancellation")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "X5 Tom (not the requester) cancels Lena's request -> 403 NOT_REQUESTER, request stays OPEN -- B3-S03 US-CA-03 ADR0013 op cancelHelpRequest")
  @Test
  void onlyTheRequesterCancels() throws Exception {
    String tomAsCook =
        TestTokens.forCook(new CookId(UUID.fromString("0e1b9f4a-7c2d-4e8b-9a61-3f5d2c7b8e10")))
            .roles("cook")
            .scope("help:write")
            .bearer();

    mvc.perform(post(CANCELLATION_PATH).header(AUTHORIZATION, tomAsCook).header("version", "1.0.7"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_REQUESTER"));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
  }

  @DisplayName(
      "X6 Lena cancels a CANCELLED request -> 409 INVALID_STATE with rule LIFECYCLE -- B3-S03 US-CA-03 INV-08 op cancelHelpRequest")
  @Test
  void cancelledRequestIsAConflict() throws Exception {
    mvc.perform(
            post(CANCELLATION_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isOk());

    mvc.perform(
            post(CANCELLATION_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_STATE"))
        .andExpect(jsonPath("$.rule").value("LIFECYCLE"));
  }

  @DisplayName(
      "X7 conflict on both tries -> 409 CONCURRENT_UPDATE -- B3-S03 US-CA-03 ADR0010 op cancelHelpRequest")
  @Test
  void conflictOnBothTriesIsAConcurrentUpdate() throws Exception {
    helpRequests.interleave(
        () -> {
          grandmaAnswersMeanwhile(HelpRequests.HELP_ID);
          helpRequests.interleave(() -> grandmaAnswersMeanwhile(HelpRequests.OTHER_HELP_ID));
        });

    mvc.perform(
            post(CANCELLATION_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
  }

  /** Another writer: Grandma answers the same request and saves first. */
  private void grandmaAnswersMeanwhile(HelpId helpId) {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    meanwhile.provideHelp(
        helpId,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        HelpRequests.FRIDAY_18_10);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "X8 no token -> 401 UNAUTHENTICATED; scope help:read only -> 403 INSUFFICIENT_SCOPE; request stays OPEN -- B3-S03 US-CA-03 ADR0012 ADR0013 op cancelHelpRequest")
  @Test
  void noTokenOrReadScopeCannotCancel() throws Exception {
    mvc.perform(post(CANCELLATION_PATH).header("version", "1.0.7"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            post(CANCELLATION_PATH)
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer())
                .header("version", "1.0.7"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.OPEN);
  }

  @DisplayName(
      "X9 unexpected error -> 500 INTERNAL_ERROR without the internal message -- B3-S03 US-CA-03 op cancelHelpRequest")
  @Test
  void unexpectedErrorHidesItsMessage() throws Exception {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    mvc.perform(
            post(CANCELLATION_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("connection refused"))));
  }
}
