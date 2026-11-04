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

/**
 * Operation acceptHelp (tag Helps) through the real web layer, real resource-server security and
 * the step 8 use case with the contract-checked fake (no database).
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class AcceptHelpOperationTest {

  static final String REQUEST_ID = HelpRequests.HELP_REQUEST_ID.value().toString();
  static final String ACCEPTANCE_PATH =
      "/help-requests/" + REQUEST_ID + "/helps/" + HelpRequests.HELP_ID.value() + "/acceptance";
  static final String LENA_AS_COOK =
      TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:write").bearer();

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
      "A1 Lena accepts Grandma's Help -> 200 {helpRequestLink: .../help-requests/<id>} matching HelpRequestLink; stored CLOSED with the Help accepted -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void lenaAcceptsGrandmasHelp() throws Exception {
    MvcResult result =
        mvc.perform(
                post(ACCEPTANCE_PATH)
                    .header(AUTHORIZATION, LENA_AS_COOK)
                    .header("version", "1.0.7"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.helpRequestId").value(REQUEST_ID))
            .andExpect(
                jsonPath("$.helpRequestLink").value("http://localhost/help-requests/" + REQUEST_ID))
            .andReturn();

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpRequestLink");
    HelpRequest stored = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    assertThat(stored.status()).isEqualTo(HelpRequestStatus.CLOSED);
    assertThat(stored.helps())
        .singleElement()
        .satisfies(help -> assertThat(help.isAccepted()).isTrue());
  }

  @DisplayName(
      "A2 helpId 'not-a-uuid' -> 400 VALIDATION_FAILED, request stays ANSWERED -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void helpIdThatIsNoUuidIsRefused() throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps/not-a-uuid/acceptance")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

    assertStillAnswered();
  }

  private void assertStillAnswered() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().status())
        .isEqualTo(HelpRequestStatus.ANSWERED);
  }

  @DisplayName("A3 unknown help request -> 404 NOT_FOUND -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void unknownRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/00000000-0000-7000-8000-000000000009/helps/"
                    + HelpRequests.HELP_ID.value()
                    + "/acceptance")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "A4 a helpId that belongs to no Help of Lena's request -> 404 NOT_FOUND with rule INV-05 (not 422: the spec declares none) -- B3-S04 US-CA-03 INV-05 op acceptHelp")
  @Test
  void helpOfAnotherRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/"
                    + REQUEST_ID
                    + "/helps/"
                    + HelpRequests.OTHER_HELP_ID.value()
                    + "/acceptance")
                .header(AUTHORIZATION, LENA_AS_COOK)
                .header("version", "1.0.7"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.rule").value("INV-05"));

    assertStillAnswered();
  }

  @DisplayName(
      "A5 Tom (not the requester) accepts the Help on Lena's request -> 403 NOT_REQUESTER, request stays ANSWERED -- B3-S04 US-CA-03 ADR0013 op acceptHelp")
  @Test
  void onlyTheRequesterAccepts() throws Exception {
    String tomAsCook =
        TestTokens.forCook(new CookId(UUID.fromString("0e1b9f4a-7c2d-4e8b-9a61-3f5d2c7b8e10")))
            .roles("cook")
            .scope("help:write")
            .bearer();

    mvc.perform(post(ACCEPTANCE_PATH).header(AUTHORIZATION, tomAsCook).header("version", "1.0.7"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_REQUESTER"));

    assertStillAnswered();
  }

  @DisplayName(
      "A6 Lena accepts on an OPEN request (no Help yet) -> 409 INVALID_STATE with rule LIFECYCLE -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void openRequestIsAConflict() throws Exception {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    mvc.perform(
            post(ACCEPTANCE_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INVALID_STATE"))
        .andExpect(jsonPath("$.rule").value("LIFECYCLE"));
  }

  @DisplayName(
      "A7 Lena accepts the Help she rejected before -> 409 INVALID_STATE with rule INV-39 -- B3-S04 US-CA-03 INV-39 op acceptHelp")
  @Test
  void rejectedHelpIsAConflict() throws Exception {
    HelpRequest request = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    request.reject(HelpRequests.HELP_ID, "warm water didn't help", FRIDAY_20_00);
    request.provideHelp(
        HelpRequests.OTHER_HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Warm the butter first",
        HelpRequests.BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_20_10);
    helpRequests.save(request);

    mvc.perform(
            post(ACCEPTANCE_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
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
      "A8 conflict on both tries -> 409 CONCURRENT_UPDATE -- B3-S04 US-CA-03 ADR0010 op acceptHelp")
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

    mvc.perform(
            post(ACCEPTANCE_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
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
      "A9 no token -> 401 UNAUTHENTICATED; scope help:read only -> 403 INSUFFICIENT_SCOPE; request stays ANSWERED -- B3-S04 US-CA-03 ADR0012 ADR0013 op acceptHelp")
  @Test
  void noTokenOrReadScopeCannotAccept() throws Exception {
    mvc.perform(post(ACCEPTANCE_PATH).header("version", "1.0.7"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            post(ACCEPTANCE_PATH)
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.LENA).roles("cook").scope("help:read").bearer())
                .header("version", "1.0.7"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));

    assertStillAnswered();
  }

  @DisplayName(
      "A10 unexpected error -> 500 INTERNAL_ERROR without the internal message -- B3-S04 US-CA-03 op acceptHelp")
  @Test
  void unexpectedErrorHidesItsMessage() throws Exception {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    mvc.perform(
            post(ACCEPTANCE_PATH).header(AUTHORIZATION, LENA_AS_COOK).header("version", "1.0.7"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("connection refused"))));
  }
}
