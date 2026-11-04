package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRejected;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * Operation rejectHelp (tag Helps) through the real web layer, real resource-server security and
 * the step 8 use case with the contract-checked fake (no database).
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class RejectHelpOperationTest {

  static final String REQUEST_ID = HelpRequests.HELP_REQUEST_ID.value().toString();
  static final String REJECTION_PATH =
      "/help-requests/" + REQUEST_ID + "/helps/" + HelpRequests.HELP_ID.value() + "/rejection";
  static final String LENA_AS_COOK =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();
  static final String WARM_WATER = "{\"note\": \"warm water didn't help\"}";

  @Autowired MockMvc mvc;
  @Autowired InMemoryHelpRequestRepository helpRequests;

  @BeforeEach
  void grandmaAnsweredLenasRequest() {
    helpRequests.clear();
    HelpRequest request = HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaExplainsStep4(request);
    helpRequests.save(request);
  }

  @DisplayName(
      "J1 Lena rejects Grandma's Help with note 'warm water didn't help' -> 200 {helpRequestLink: .../help-requests/<id>} matching HelpRequestLink; stored OPEN, the Help rejected with the note -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void lenaRejectsGrandmasHelp() throws Exception {
    MvcResult result =
        mvc.perform(
                post(REJECTION_PATH)
                    .header(AUTHORIZATION, LENA_AS_COOK)
                    .header("version", "1.0.7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(WARM_WATER))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.helpRequestId").value(REQUEST_ID))
            .andExpect(
                jsonPath("$.helpRequestLink").value("http://localhost/help-requests/" + REQUEST_ID))
            .andReturn();

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpRequestLink");
    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.OPEN);
    assertThat(stored.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.isRejected()).isTrue());
    assertThat(helpRequests.recordedEvents())
        .filteredOn(HelpRejected.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(
            HelpRejected.class,
            event -> assertThat(event.note()).isEqualTo("warm water didn't help"));
  }

  @DisplayName(
      "J2 body without note -> 400 VALIDATION_FAILED, request stays ANSWERED -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void missingNoteIsRefused() throws Exception {
    rejectWith("{}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertStillAnswered();
  }

  private ResultActions rejectWith(String body) throws Exception {
    return mvc.perform(
        post(REJECTION_PATH)
            .header(AUTHORIZATION, LENA_AS_COOK)
            .header("version", "1.0.7")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  private void assertStillAnswered() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName(
      "J3 note \"\" -> 400 VALIDATION_FAILED (schema minLength), request stays ANSWERED -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void emptyNoteIsRefused() throws Exception {
    rejectWith("{\"note\": \"\"}")
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertStillAnswered();
  }

  @DisplayName(
      "J4 note of spaces only -> 422 MISSING_FIELD with rule INV-06, request stays ANSWERED -- B3-S05 US-CA-03 INV-06 op rejectHelp")
  @Test
  void blankNoteIsARuleViolation() throws Exception {
    rejectWith("{\"note\": \"   \"}")
        .andExpect(status().is(422))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MISSING_FIELD"))
        .andExpect(jsonPath("$.rule").value("INV-06"));

    assertStillAnswered();
  }

  @DisplayName(
      "J5 a rejection with nextChef on a preparation-step request -> 501 NOT_IMPLEMENTED (menu requests, increment 4), request stays ANSWERED -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void nextChefIsNotImplementedYet() throws Exception {
    rejectWith(
            "{\"note\": \"warm water didn't help\", \"nextChef\": \"3c4d5e6f-7a8b-4c9d-8e0f-1a2b3c4d5e6f\"}")
        .andExpect(status().isNotImplemented())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"));

    assertStillAnswered();
  }

  @DisplayName("J6 unknown help request -> 404 NOT_FOUND -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void unknownRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/00000000-0000-7000-8000-000000000009/helps/"
                    + HelpRequests.HELP_ID.value()
                    + "/rejection")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WARM_WATER))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "J7 a helpId that belongs to no Help of Lena's request -> 404 NOT_FOUND with rule INV-05 -- B3-S05 US-CA-03 INV-05 op rejectHelp")
  @Test
  void helpOfAnotherRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/"
                    + REQUEST_ID
                    + "/helps/"
                    + HelpRequests.OTHER_HELP_ID.value()
                    + "/rejection")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WARM_WATER))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.rule").value("INV-05"));

    assertStillAnswered();
  }

  @DisplayName(
      "J8 Tom (not the requester) rejects the Help on Lena's request -> 403 NOT_REQUESTER, request stays ANSWERED -- B3-S05 US-CA-03 ADR0013 op rejectHelp")
  @Test
  void onlyTheRequesterRejects() throws Exception {
    String tomAsCook =
        TestTokens.forCook(new CookId(UUID.fromString("0e1b9f4a-7c2d-4e8b-9a61-3f5d2c7b8e10")))
            .roles("cook")
            .scope("help:write")
            .bearer();

    mvc.perform(
            post(REJECTION_PATH)
                .header(AUTHORIZATION, tomAsCook)
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WARM_WATER))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_REQUESTER"));

    assertStillAnswered();
  }

  @DisplayName(
      "J9 Lena rejects on an OPEN request (no Help yet) -> 409 INVALID_STATE with rule LIFECYCLE -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void openRequestIsAConflict() throws Exception {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    rejectWith(WARM_WATER)
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_STATE"))
        .andExpect(jsonPath("$.rule").value("LIFECYCLE"));
  }

  @DisplayName(
      "J10 Lena rejects the same Help twice (Grandma answered again in between) -> 409 INVALID_STATE with rule INV-39 -- B3-S05 US-CA-03 INV-39 op rejectHelp")
  @Test
  void rejectingTwiceIsAConflict() throws Exception {
    HelpRequest request = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    request.reject(HelpRequests.HELP_ID, "warm water didn't help", FRIDAY_20_00);
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_20_10);
    helpRequests.save(request);

    rejectWith(WARM_WATER)
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_STATE"))
        .andExpect(jsonPath("$.rule").value("INV-39"));

    assertStillAnswered();
  }

  static final Clock FRIDAY_20_00 =
      Clock.fixed(Instant.parse("2026-11-06T19:00:00Z"), ZoneOffset.UTC);
  static final Clock FRIDAY_20_10 =
      Clock.fixed(Instant.parse("2026-11-06T19:10:00Z"), ZoneOffset.UTC);

  @DisplayName(
      "J11 conflict on both tries -> 409 CONCURRENT_UPDATE -- B3-S05 US-CA-03 ADR0010 op rejectHelp")
  @Test
  void conflictOnBothTriesIsAConcurrentUpdate() throws Exception {
    helpRequests.interleave(
        () -> {
          grandmaAnswersAgainMeanwhile(HelpRequests.OTHER_HELP_ID);
          helpRequests.interleave(
              () ->
                  grandmaAnswersAgainMeanwhile(
                      new HelpId(UUID.fromString("6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b93"))));
        });

    rejectWith(WARM_WATER)
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
  }

  /** Another writer: Grandma answers the same request once more and saves first. */
  private void grandmaAnswersAgainMeanwhile(HelpId helpId) {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    meanwhile.provideHelp(
        helpId,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_20_10);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "J12 no token -> 401 UNAUTHENTICATED; scope help:read only -> 403 INSUFFICIENT_SCOPE; request stays ANSWERED -- B3-S05 US-CA-03 ADR0012 ADR0013 op rejectHelp")
  @Test
  void noTokenOrReadScopeCannotReject() throws Exception {
    mvc.perform(
            post(REJECTION_PATH)
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WARM_WATER))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            post(REJECTION_PATH)
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer())
                .header("version", "1.0.7")
                .contentType(MediaType.APPLICATION_JSON)
                .content(WARM_WATER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));

    assertStillAnswered();
  }

  @DisplayName(
      "J13 unexpected error -> 500 INTERNAL_ERROR without the internal message -- B3-S05 US-CA-03 op rejectHelp")
  @Test
  void unexpectedErrorHidesItsMessage() throws Exception {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    rejectWith(WARM_WATER)
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("connection refused"))));
  }
}
