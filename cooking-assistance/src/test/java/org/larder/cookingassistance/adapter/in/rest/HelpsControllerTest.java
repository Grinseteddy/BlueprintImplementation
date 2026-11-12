package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpAnswer;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.Substitute;
import org.larder.cookingassistance.domain.helprequest.SubstituteIngredient;
import org.larder.cookingassistance.domain.helprequest.Substitutes;
import org.larder.cookingassistance.domain.helprequest.Unit;
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
 * Operations of tag Helps (provideHelp) through the real web layer, real resource-server security
 * and the step 8 use cases with contract-checked fakes (no database).
 */
@WebMvcTest
@Import({RestTestConfiguration.class, SecurityConfiguration.class, TestSigningKeys.class})
class HelpsControllerTest {

  static final String REQUEST_ID = HelpRequests.HELP_REQUEST_ID.value().toString();
  static final String JONAS_AS_COMMUNITY =
      TestTokens.forCook(HelpRequests.JONAS)
          .roles("cook", "community")
          .scope("help:write")
          .bearer();
  static final String JONAS_ANSWER =
      """
      {
        "answerTitle": "Warm the bowl",
        "helpProviderType": "COMMUNITY",
        "answer": {
          "answerType": "PREPARATION_STEP_EXPLANATION",
          "recipe": "%s",
          "howToStep": "%s",
          "description": "Add the butter slowly to a warm bowl",
          "images": []
        }
      }
      """
          .formatted(HelpRequests.HOLLANDAISE.value(), HelpRequests.STEP_4.value());

  @Autowired MockMvc mvc;
  @Autowired InMemoryHelpRequestRepository helpRequests;

  @BeforeEach
  void lenasRequestToTheCommunityIsStored() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY));
  }

  @DisplayName(
      "Jonas (community, help:write) answers Lena's PSE request as COMMUNITY -> 201 {helpLink: .../help-requests/<id>/helps/<helpId>} matching HelpLink; the help is stored as Jonas's -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void jonasProvidesHelp() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/help-requests/" + REQUEST_ID + "/helps")
                    .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                    .header("version", "1.0.3")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JONAS_ANSWER))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.helpLink")
                    .value(
                        "http://localhost/help-requests/"
                            + REQUEST_ID
                            + "/helps/"
                            + HelpRequests.HELP_ID.value()))
            .andReturn();

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpLink");
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(help -> assertThat(help.provider()).contains(HelpRequests.JONAS));
  }

  @DisplayName(
      "answer without images -> 201; the help has no pictures -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void answerWithoutImagesHasNoPictures() throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER.replaceAll(",\\s*\"images\": \\[\\]", "")))
        .andExpect(status().isCreated());

    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .singleElement()
        .satisfies(
            help -> assertThat(((PreparationStepExplanation) help.answer()).images()).isEmpty());
  }

  private void expectValidationFailed(String body) throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    assertNoHelpSaved();
  }

  private static String elevenImages() {
    return IntStream.rangeClosed(1, 11)
        .mapToObj(i -> "\"https://larder.org/media/images/photo-" + i + "\"")
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private void assertNoHelpSaved() {
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps()).isEmpty();
  }

  @DisplayName(
      "answerTitle missing -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void missingAnswerTitleIsRejected() throws Exception {
    expectValidationFailed(JONAS_ANSWER.replace("\"answerTitle\": \"Warm the bowl\",", ""));
  }

  @DisplayName(
      "helpProviderType \"ROBOT\" -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void invalidProviderTypeIsRejected() throws Exception {
    expectValidationFailed(
        JONAS_ANSWER.replace(
            "\"helpProviderType\": \"COMMUNITY\"", "\"helpProviderType\": \"ROBOT\""));
  }

  @DisplayName(
      "PSE answer without description -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void explanationWithoutDescriptionIsRejected() throws Exception {
    expectValidationFailed(
        JONAS_ANSWER.replace("\"description\": \"Add the butter slowly to a warm bowl\",", ""));
  }

  @DisplayName(
      "11 images -> 400 VALIDATION_FAILED, nothing saved (schema maxItems) -- B3-S02 US-CA-03 INV-38 op provideHelp")
  @Test
  void elevenImagesAreRejected() throws Exception {
    expectValidationFailed(JONAS_ANSWER.replace("\"images\": []", "\"images\": " + elevenImages()));
  }

  @DisplayName(
      "image link javascript:alert(1) -> 400 VALIDATION_FAILED, nothing saved (schema: https only) -- B3-S02 US-CA-03 INV-38 OWASP-API10 op provideHelp")
  @Test
  void nonHttpsImageIsRejected() throws Exception {
    expectValidationFailed(
        JONAS_ANSWER.replace("\"images\": []", "\"images\": [\"javascript:alert(1)\"]"));
  }

  @DisplayName("unknown help request -> 404 NOT_FOUND -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void unknownHelpRequestIsNotFound() throws Exception {
    mvc.perform(
            post("/help-requests/00000000-0000-7000-8000-000000000009/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @DisplayName(
      "Lena answers her own request -> 403 OWN_REQUEST with rule INV-36 -- B3-S02 US-CA-03 INV-36 op provideHelp")
  @Test
  void ownRequestIsForbidden() throws Exception {
    String lenaAsCommunity =
        TestTokens.forCook(HelpRequests.LENA)
            .roles("cook", "community")
            .scope("help:write")
            .bearer();

    expectRejection(lenaAsCommunity, JONAS_ANSWER, 403, "OWN_REQUEST", "INV-36");
  }

  private void expectRejection(String bearer, String body, int status, String code, String rule)
      throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, bearer)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().is(status))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.rule").value(rule));
    assertNoHelpSaved();
  }

  @DisplayName(
      "request sent only to Grandma Avatar -> 403 PROVIDER_NOT_ASKED with rule INV-01 -- B3-S02 US-CA-03 INV-01 op provideHelp")
  @Test
  void providerNotAskedIsForbidden() throws Exception {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksAboutStep4(HelpProviderType.GRANDMA_AVATAR));

    expectRejection(JONAS_AS_COMMUNITY, JONAS_ANSWER, 403, "PROVIDER_NOT_ASKED", "INV-01");
  }

  @DisplayName(
      "CANCELLED request -> 409 INVALID_STATE with rule LIFECYCLE -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void cancelledRequestIsAConflict() throws Exception {
    HelpRequest open = HelpRequests.lenaAsksAboutStep4(HelpProviderType.COMMUNITY);
    helpRequests.clear();
    helpRequests.save(
        HelpRequest.reconstitute(
            open.id(),
            open.requester(),
            open.title(),
            open.description(),
            open.type(),
            open.recipe(),
            open.howToStep(),
            open.askedProviders(),
            HelpRequestStatus.CANCELLED,
            List.of(),
            List.of(),
            0L));

    expectRejection(JONAS_AS_COMMUNITY, JONAS_ANSWER, 409, "INVALID_STATE", "LIFECYCLE");
  }

  @DisplayName(
      "answer about step 6 -> 422 ANSWER_DOES_NOT_MATCH_REQUEST with rule INV-37 -- B3-S02 US-CA-03 INV-37 op provideHelp")
  @Test
  void answerAboutAnotherStepIsARuleViolation() throws Exception {
    String aboutStep6 =
        JONAS_ANSWER.replace(
            HelpRequests.STEP_4.value().toString(), "c3d4e5f6-a7b8-4c9d-8e0f-2a3b4c5d6e7f");

    expectRejection(JONAS_AS_COMMUNITY, aboutStep6, 422, "ANSWER_DOES_NOT_MATCH_REQUEST", "INV-37");
  }

  @DisplayName(
      "conflict on both tries -> 409 CONCURRENT_UPDATE -- B3-S02 US-CA-03 ADR0010 op provideHelp")
  @Test
  void conflictOnBothTriesIsAConcurrentUpdate() throws Exception {
    helpRequests.interleave(
        () -> {
          otherMemberAnswersMeanwhile(HelpRequests.OTHER_HELP_ID, HelpRequests.MIA);
          helpRequests.interleave(
              () ->
                  otherMemberAnswersMeanwhile(
                      new HelpId(UUID.fromString("6f7a8b9c-0d1e-4f2a-9b3c-4d5e6f7a8b92")), SAM));
        });

    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
  }

  static final CookId SAM = new CookId(UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a81"));

  /** Another writer answers the same request and saves first. */
  private void otherMemberAnswersMeanwhile(HelpId helpId, CookId member) {
    HelpRequest meanwhile = helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow();
    HelpRequests.communityMemberExplainsStep4(meanwhile, helpId, member);
    helpRequests.save(meanwhile);
  }

  @DisplayName(
      "helpProviderType GRANDMA_AVATAR, then CHEF -> 403 NOT_AUTHORIZED each -- B3-S02 US-CA-03 ADR0013 op provideHelp")
  @Test
  void grandmaAvatarOrChefThroughTheApiIsRefused() throws Exception {
    for (String providerType : List.of("GRANDMA_AVATAR", "CHEF")) {
      expectNotAuthorized(
          JONAS_AS_COMMUNITY,
          JONAS_ANSWER.replace(
              "\"helpProviderType\": \"COMMUNITY\"",
              "\"helpProviderType\": \"" + providerType + "\""));
    }
  }

  private void expectNotAuthorized(String bearer, String body) throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, bearer)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("NOT_AUTHORIZED"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "actor without role community -> 403 NOT_AUTHORIZED, nothing saved -- B3-S02 US-CA-03 ADR0013 op provideHelp")
  @Test
  void actorWithoutCommunityRoleIsRefused() throws Exception {
    expectNotAuthorized(
        TestTokens.forCook(HelpRequests.JONAS).roles("cook").scope("help:write").bearer(),
        JONAS_ANSWER);
  }

  @DisplayName(
      "no token -> 401 UNAUTHENTICATED; scope help:read only -> 403 INSUFFICIENT_SCOPE -- B3-S02 US-CA-03 ADR0012 ADR0013 op provideHelp")
  @Test
  void noTokenOrReadScopeCannotProvideHelp() throws Exception {
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(
                    AUTHORIZATION,
                    TestTokens.forCook(HelpRequests.JONAS)
                        .roles("cook", "community")
                        .scope("help:read")
                        .bearer())
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"));
    assertNoHelpSaved();
  }

  @DisplayName(
      "unexpected error -> 500 INTERNAL_ERROR without the internal message -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void unexpectedErrorHidesItsMessage() throws Exception {
    helpRequests.interleave(
        () -> {
          throw new RuntimeException("connection refused for user larder");
        });

    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JONAS_ANSWER))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(content().string(not(containsString("connection refused"))));
  }

  @DisplayName(
      "Jonas proposes 200 g yogurt on Lena's ingredient request -> 201 with the link; the Help holds the Substitutes (replaces the 501 placeholder) -- B2-S02 US-CA-02 INV-12 R3.E1 op provideHelp")
  @Test
  void jonasProposesSubstitutes() throws Exception {
    lenasIngredientRequestIsStored();

    MvcResult result = post201(proposing(substitute("yogurt", "200", "GRAM")));

    SpecSchemas.assertMatches(result.getResponse().getContentAsString(), "HelpLink");
    assertThat(storedAnswer())
        .isEqualTo(
            new Substitutes(
                HelpRequests.TOMATO_SOUP, List.of(HelpRequests.gramsOf("yogurt", "200"))));
  }

  @DisplayName(
      "a substitute of 100 g mascarpone and 100 g yogurt -> both reach the Help in order -- B2-S02 US-CA-02 INV-12 R3.E3 op provideHelp")
  @Test
  void aMixArrivesWithItsIngredientsInOrder() throws Exception {
    lenasIngredientRequestIsStored();
    String mascarpone = "{\"name\": \"mascarpone\", \"value\": 100, \"unit\": \"GRAM\"}";
    String yogurt = "{\"name\": \"yogurt\", \"value\": 100, \"unit\": \"GRAM\"}";

    post201(proposing(mix(mascarpone, yogurt)));

    assertThat(storedAnswer())
        .isEqualTo(
            new Substitutes(
                HelpRequests.TOMATO_SOUP,
                List.of(
                    new Substitute(
                        HelpRequests.CREME_FRAICHE,
                        List.of(
                            new SubstituteIngredient(
                                "mascarpone", new BigDecimal("100"), Unit.GRAM),
                            new SubstituteIngredient(
                                "yogurt", new BigDecimal("100"), Unit.GRAM))))));
  }

  @DisplayName(
      "a substitute ingredient in each unit of the contract -> the same unit in the Help -- B2-S02 US-CA-02 INV-12 op provideHelp")
  @ParameterizedTest
  @EnumSource(Unit.class)
  void everyUnitOfTheContractArrives(Unit unit) throws Exception {
    lenasIngredientRequestIsStored();

    post201(proposing(substitute("quark", "2", unit.name())));

    assertThat(storedAnswer())
        .isEqualTo(
            new Substitutes(
                HelpRequests.TOMATO_SOUP,
                List.of(
                    new Substitute(
                        HelpRequests.CREME_FRAICHE,
                        List.of(new SubstituteIngredient("quark", new BigDecimal("2"), unit))))));
  }

  @DisplayName(
      "an amount of 0.125 -> exactly 0.125 in the Help -- B2-S02 US-CA-02 INV-12 op provideHelp")
  @Test
  void anAmountArrivesExactly() throws Exception {
    lenasIngredientRequestIsStored();

    post201(proposing(substitute("quark", "0.125", "KILOGRAM")));

    assertThat(storedAnswer())
        .isEqualTo(
            new Substitutes(
                HelpRequests.TOMATO_SOUP,
                List.of(
                    new Substitute(
                        HelpRequests.CREME_FRAICHE,
                        List.of(
                            new SubstituteIngredient(
                                "quark", new BigDecimal("0.125"), Unit.KILOGRAM))))));
  }

  @DisplayName(
      "no substitute (minItems) -> 400 VALIDATION_FAILED, no Help -- B2-S02 US-CA-02 INV-12 op provideHelp")
  @Test
  void anEmptySubstituteListIsRejected() throws Exception {
    lenasIngredientRequestIsStored();

    expectValidationFailed(proposing());
  }

  @DisplayName(
      "an empty name (minLength) -> 400 VALIDATION_FAILED, no Help -- B2-S02 US-CA-02 INV-12 op provideHelp")
  @Test
  void anEmptyNameIsRejected() throws Exception {
    lenasIngredientRequestIsStored();

    expectValidationFailed(proposing(substitute("", "200", "GRAM")));
  }

  @DisplayName(
      "a name of only spaces -> 422 VALIDATION_FAILED, rule INV-12, no Help -- B2-S02 US-CA-02 INV-12 (step 3 r13) op provideHelp")
  @Test
  void aBlankNameIsARuleViolation() throws Exception {
    lenasIngredientRequestIsStored();

    expectRejection(
        JONAS_AS_COMMUNITY,
        proposing(substitute("   ", "200", "GRAM")),
        422,
        "VALIDATION_FAILED",
        "INV-12");
  }

  @DisplayName(
      "an amount of 0 (exclusiveMinimum) -> 400 VALIDATION_FAILED, no Help -- B2-S02 US-CA-02 op provideHelp")
  @Test
  void anAmountOfZeroIsRejected() throws Exception {
    lenasIngredientRequestIsStored();

    expectValidationFailed(proposing(substitute("yogurt", "0", "GRAM")));
  }

  @DisplayName(
      "an amount above 10,000 (maximum) -> 400 VALIDATION_FAILED, no Help: 1e1500 would break every later read of the request, 1e400 its published event -- B2-S02 US-CA-02 INV-12 SEC-20 SEC-21 OWASP-API4 OWASP-A08 op provideHelp")
  @ParameterizedTest
  @ValueSource(strings = {"10000.001", "1e400", "1e1500"})
  void anAmountAboveTheMaximumIsRejected(String amount) throws Exception {
    lenasIngredientRequestIsStored();

    expectValidationFailed(proposing(substitute("yogurt", amount, "GRAM")));
  }

  @DisplayName(
      "an amount with 4 decimal places (the schema allows it) -> 422 VALIDATION_FAILED, rule INV-12, no Help -- B2-S02 US-CA-02 INV-12 SEC-20 (step 3 r19) op provideHelp")
  @Test
  void anAmountWithMoreThanThreeDecimalPlacesIsARuleViolation() throws Exception {
    lenasIngredientRequestIsStored();

    expectRejection(
        JONAS_AS_COMMUNITY,
        proposing(substitute("yogurt", "0.0001", "KILOGRAM")),
        422,
        "VALIDATION_FAILED",
        "INV-12");
  }

  @DisplayName(
      "a unit the contract does not list -> 400 VALIDATION_FAILED, no Help -- B2-S02 US-CA-02 op provideHelp")
  @Test
  void anUnknownUnitIsRejected() throws Exception {
    lenasIngredientRequestIsStored();

    expectValidationFailed(proposing(substitute("yogurt", "200", "BUCKET")));
  }

  @DisplayName(
      "substitutes for another ingredient -> 422 ANSWER_DOES_NOT_MATCH_REQUEST, rule INV-37, no Help -- B2-S02 US-CA-02 INV-37 op provideHelp")
  @Test
  void substitutesForAnotherIngredientAreARuleViolation() throws Exception {
    lenasIngredientRequestIsStored();
    String forBacon =
        substitute("yogurt", "200", "GRAM")
            .replace(
                HelpRequests.CREME_FRAICHE.value().toString(),
                "5b1f0a2e-7c3d-4e8f-9a6b-1d2c3e4f5a6b");

    expectRejection(
        JONAS_AS_COMMUNITY, proposing(forBacon), 422, "ANSWER_DOES_NOT_MATCH_REQUEST", "INV-37");
  }

  @DisplayName(
      "Lena rejected yogurt; Jonas proposes yogurt -> 422 SUBSTITUTE_REJECTED_BEFORE, rule INV-15, no further Help -- B3-S05 US-CA-02 INV-15 op provideHelp")
  @Test
  void aRejectedSubstituteIsARuleViolation() throws Exception {
    helpRequests.clear();
    HelpRequest request =
        HelpRequests.lenaAsksForASubstitute(
            HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);
    HelpRequests.grandmaProposes(request, HelpRequests.gramsOf("yogurt", "200"));
    request.reject(
        HelpRequests.HELP_ID,
        "too sour",
        Clock.offset(HelpRequests.MONDAY_18_00, Duration.ofHours(2)));
    helpRequests.save(request);

    mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(proposing(substitute("yogurt", "200", "GRAM"))))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("SUBSTITUTE_REJECTED_BEFORE"))
        .andExpect(jsonPath("$.rule").value("INV-15"));
    assertThat(helpRequests.findById(HelpRequests.HELP_REQUEST_ID).orElseThrow().helps())
        .hasSize(1);
  }

  private void lenasIngredientRequestIsStored() {
    helpRequests.clear();
    helpRequests.save(HelpRequests.lenaAsksForASubstitute(HelpProviderType.COMMUNITY));
  }

  /** One substitute of one ingredient, as the contract has it. */
  private static String substitute(String name, String value, String unit) {
    return mix("{\"name\": \"%s\", \"value\": %s, \"unit\": \"%s\"}".formatted(name, value, unit));
  }

  private static String mix(String... ingredients) {
    return "{\"ingredient\": \"%s\", \"substituteIngredients\": [%s]}"
        .formatted(HelpRequests.CREME_FRAICHE.value(), String.join(", ", ingredients));
  }

  /** Jonas's answer to the ingredient request, proposing the given substitutes. */
  private static String proposing(String... substitutes) {
    return """
        {
          "answerTitle": "Use this",
          "helpProviderType": "COMMUNITY",
          "answer": {
            "answerType": "INGREDIENT_SUBSTITUTE",
            "recipe": "%s",
            "substitute": [%s]
          }
        }
        """
        .formatted(HelpRequests.TOMATO_SOUP.value(), String.join(", ", substitutes));
  }

  private MvcResult post201(String body) throws Exception {
    return mvc.perform(
            post("/help-requests/" + REQUEST_ID + "/helps")
                .header(AUTHORIZATION, JONAS_AS_COMMUNITY)
                .header("version", "1.0.3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andReturn();
  }

  private HelpAnswer storedAnswer() {
    return helpRequests
        .findById(HelpRequests.HELP_REQUEST_ID)
        .orElseThrow()
        .helps()
        .getFirst()
        .answer();
  }
}
