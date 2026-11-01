package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.larder.cookingassistance.domain.helprequest.AnswerDoesNotMatchRequest;
import org.larder.cookingassistance.domain.helprequest.ChefNotAllowed;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.domain.helprequest.NoHelpProviderChosen;
import org.larder.cookingassistance.domain.helprequest.OwnRequest;
import org.larder.cookingassistance.domain.helprequest.PreparationStepRequired;
import org.larder.cookingassistance.domain.helprequest.ProviderNotAsked;
import org.larder.cookingassistance.domain.helprequest.RecipeRequired;
import org.larder.cookingassistance.domain.helprequest.TooManyPictures;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** The error map (OpenAPI 1.0.3 Problem codes): each domain rejection -> status, code and rule. */
class ProblemDetailsAdviceTest {

  static Stream<Arguments> rejections() {
    return Stream.of(
        Arguments.of(new ChefNotAllowed(), 422, "CHEF_NOT_ALLOWED", "INV-01"),
        Arguments.of(new NoHelpProviderChosen(), 422, "VALIDATION_FAILED", "INV-01"),
        Arguments.of(new RecipeRequired(), 422, "MISSING_FIELD", "INV-02"),
        Arguments.of(new PreparationStepRequired(), 422, "MISSING_FIELD", "INV-02"),
        Arguments.of(new TooManyPictures(), 422, "TOO_MANY_PICTURES", "INV-38"),
        Arguments.of(new AnswerDoesNotMatchRequest(), 422, "ANSWER_DOES_NOT_MATCH_REQUEST", "INV-37"),
        Arguments.of(new OwnRequest(), 403, "OWN_REQUEST", "INV-36"),
        Arguments.of(new ProviderNotAsked(), 403, "PROVIDER_NOT_ASKED", "INV-01"),
        Arguments.of(
            new InvalidHelpRequestState(HelpRequestStatus.CANCELLED), 409, "INVALID_STATE", "LIFECYCLE"));
  }

  @DisplayName("each of the 9 domain rejections -> its status (OWN_REQUEST, PROVIDER_NOT_ASKED 403; INVALID_STATE 409; the rest 422), its code and its rule -- B3-S01,B3-S02 US-CA-03 op createHelpRequest,provideHelp")
  @ParameterizedTest(name = "{2} -> {1}")
  @MethodSource("rejections")
  void domainRejectionsMapToStatusCodeAndRule(
      DomainRejection rejection, int status, String code, String rule) {
    ResponseEntity<Object> response = new ProblemDetailsAdvice().handleDomainRejection(rejection);

    assertThat(response.getStatusCode().value()).isEqualTo(status);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getStatus()).isEqualTo(status);
    assertThat(problem.getProperties())
        .containsEntry("code", code)
        .containsEntry("rule", rule);
  }
}
