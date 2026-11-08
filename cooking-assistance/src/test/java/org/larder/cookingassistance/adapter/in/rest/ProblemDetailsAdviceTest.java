package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.larder.cookingassistance.domain.helprequest.AnswerDoesNotMatchRequest;
import org.larder.cookingassistance.domain.helprequest.AnswerTooLate;
import org.larder.cookingassistance.domain.helprequest.ChefNotAllowed;
import org.larder.cookingassistance.domain.helprequest.DeadlineNotPassed;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.larder.cookingassistance.domain.helprequest.HelpAlreadyRejected;
import org.larder.cookingassistance.domain.helprequest.HelpNotInRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.InvalidHelpRequestState;
import org.larder.cookingassistance.domain.helprequest.NoHelpProviderChosen;
import org.larder.cookingassistance.domain.helprequest.OwnRequest;
import org.larder.cookingassistance.domain.helprequest.PictureLinkNotSecure;
import org.larder.cookingassistance.domain.helprequest.PreparationStepRequired;
import org.larder.cookingassistance.domain.helprequest.ProviderNotAsked;
import org.larder.cookingassistance.domain.helprequest.RecipeRequired;
import org.larder.cookingassistance.domain.helprequest.RejectionNoteRequired;
import org.larder.cookingassistance.domain.helprequest.TooManyPictures;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

/**
 * The error map (OpenAPI 1.0.11 Problem codes, P11): each domain rejection -> status, code and
 * rule.
 */
class ProblemDetailsAdviceTest {

  static Stream<Arguments> rejections() {
    return Stream.of(
        Arguments.of(new ChefNotAllowed(), 422, "CHEF_NOT_ALLOWED", "INV-01"),
        Arguments.of(new NoHelpProviderChosen(), 422, "VALIDATION_FAILED", "INV-01"),
        Arguments.of(new RecipeRequired(), 422, "MISSING_FIELD", "INV-02"),
        Arguments.of(new PreparationStepRequired(), 422, "MISSING_FIELD", "INV-02"),
        Arguments.of(new TooManyPictures(), 422, "TOO_MANY_PICTURES", "INV-38"),
        Arguments.of(new PictureLinkNotSecure(), 422, "INVALID_PICTURE_LINK", "INV-38"),
        Arguments.of(
            new AnswerDoesNotMatchRequest(), 422, "ANSWER_DOES_NOT_MATCH_REQUEST", "INV-37"),
        Arguments.of(new OwnRequest(), 403, "OWN_REQUEST", "INV-36"),
        Arguments.of(new ProviderNotAsked(), 403, "PROVIDER_NOT_ASKED", "INV-01"),
        Arguments.of(
            new InvalidHelpRequestState(HelpRequestStatus.CANCELLED),
            409,
            "INVALID_STATE",
            "LIFECYCLE"),
        Arguments.of(new AnswerTooLate(), 409, "INVALID_STATE", "INV-09"),
        Arguments.of(new DeadlineNotPassed(), 409, "INVALID_STATE", "INV-09"),
        Arguments.of(new HelpAlreadyRejected(), 409, "INVALID_STATE", "INV-39"),
        Arguments.of(new HelpNotInRequest(), 404, "NOT_FOUND", "INV-05"),
        Arguments.of(new RejectionNoteRequired(), 422, "MISSING_FIELD", "INV-06"));
  }

  @DisplayName(
      "each of the 15 domain rejections -> its status (OWN_REQUEST, PROVIDER_NOT_ASKED 403; NOT_FOUND 404; INVALID_STATE 409; the rest 422), its code and its rule -- B3-S01,B3-S02,B3-S03,B3-S04,B3-S05,B3-S06 US-CA-03 op createHelpRequest,provideHelp,cancelHelpRequest,acceptHelp,rejectHelp")
  @ParameterizedTest(name = "{2} -> {1}")
  @MethodSource("rejections")
  void domainRejectionsMapToStatusCodeAndRule(
      DomainRejection rejection, int status, String code, String rule) {
    ResponseEntity<Object> response = new ProblemDetailsAdvice().handleDomainRejection(rejection);

    assertThat(response.getStatusCode().value()).isEqualTo(status);
    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(problem.getStatus()).isEqualTo(status);
    assertThat(problem.getProperties()).containsEntry("code", code).containsEntry("rule", rule);
  }

  @DisplayName(
      "no database connection (CannotGetJdbcConnectionException) -> 503 SERVICE_UNAVAILABLE Problem, no detail -- B3-S01 US-CA-03 QS-05 (step 9 r1 from step 12, DatabaseOutageIT)")
  @Test
  void missingDatabaseConnectionIsServiceUnavailable() {
    ResponseEntity<Object> response = new ProblemDetailsAdvice().handleDatabaseUnavailable();

    ProblemDetail problem = (ProblemDetail) response.getBody();
    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(problem.getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
    assertThat(problem.getDetail()).isNull();
  }

  @DisplayName(
      "no connection for a transaction (CannotCreateTransactionException) -> handled as a missing database connection, 503 -- B3-S01 US-CA-03 QS-05 (step 9 r1 from step 12, DatabaseOutageIT)")
  @Test
  void noConnectionForATransactionIsServiceUnavailable() {
    Method handler =
        new ExceptionHandlerMethodResolver(ProblemDetailsAdvice.class)
            .resolveMethod(new CannotCreateTransactionException("Could not open JDBC Connection"));

    assertThat(handler).isNotNull();
    assertThat(handler.getName()).isEqualTo("handleDatabaseUnavailable");
  }

  @DisplayName(
      "a failed rollback after a lost connection (TransactionSystemException over DataAccessResourceFailureException) -> 503 SERVICE_UNAVAILABLE -- B3-S01 US-CA-03 QS-05 (step 9 r1 from step 12, DatabaseOutageIT)")
  @Test
  void failedRollbackAfterALostConnectionIsServiceUnavailable() {
    TransactionSystemException rollbackFailed =
        new TransactionSystemException("JDBC rollback failed");
    rollbackFailed.initApplicationException(
        new DataAccessResourceFailureException("Read timed out"));

    ResponseEntity<Object> response =
        new ProblemDetailsAdvice().handleTransactionSystem(rollbackFailed);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(((ProblemDetail) response.getBody()).getProperties())
        .containsEntry("code", "SERVICE_UNAVAILABLE");
  }

  @DisplayName(
      "any other TransactionSystemException -> 500 INTERNAL_ERROR, as before -- B3-S01 US-CA-03 edge (step 9 r1 from step 12)")
  @Test
  void otherTransactionFailureIsAnInternalError() {
    ResponseEntity<Object> response =
        new ProblemDetailsAdvice()
            .handleTransactionSystem(new TransactionSystemException("commit failed"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(((ProblemDetail) response.getBody()).getProperties())
        .containsEntry("code", "INTERNAL_ERROR");
  }

  private static ProblemDetail frameworkError(Exception error) throws Exception {
    ResponseEntity<Object> response =
        new ProblemDetailsAdvice()
            .handleException(error, new ServletWebRequest(new MockHttpServletRequest()));
    return (ProblemDetail) response.getBody();
  }

  @DisplayName(
      "Spring's own 500 (conversion not supported) -> 500 INTERNAL_ERROR Problem, type .../internal-error -- B3-S01,B3-S02 US-CA-03 edge")
  @Test
  void frameworkServerErrorIsAnInternalError() throws Exception {
    ProblemDetail problem =
        frameworkError(new ConversionNotSupportedException("x", Integer.class, null));

    assertThat(problem.getStatus()).isEqualTo(500);
    assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
    assertThat(problem.getType()).hasToString("https://larder.org/problems/internal-error");
  }

  @DisplayName(
      "Spring's own 400 (missing request parameter) -> 400 VALIDATION_FAILED Problem -- B3-S01,B3-S02 US-CA-03 edge")
  @Test
  void frameworkBadRequestIsAValidationFailure() throws Exception {
    ProblemDetail problem =
        frameworkError(new MissingServletRequestParameterException("limit", "Integer"));

    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getProperties()).containsEntry("code", "VALIDATION_FAILED");
  }
}
