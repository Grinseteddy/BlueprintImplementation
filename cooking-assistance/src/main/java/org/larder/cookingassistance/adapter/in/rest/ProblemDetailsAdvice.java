package org.larder.cookingassistance.adapter.in.rest;

import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.NotAuthorized;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Answers every failure with the RFC 9457 Problem of cooking-assistance.openapi.yaml: a domain
 * rejection with its own code, every other failure with its {@link NamedProblem} entry.
 */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  /** Spring's own errors this map does not name still answer the spec's Problem, with a code. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    // Spring passes a null body for most of its errors; the parent builds the Problem from them
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && !Problems.carriesCode(problem)) {
      return unnamedError(response.getStatusCode(), response.getHeaders());
    }
    return response;
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return NamedProblem.VALIDATION_FAILED.response();
  }

  /** A path or query parameter that cannot be converted breaks the contract like a bad body. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return NamedProblem.VALIDATION_FAILED.response();
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return NamedProblem.VALIDATION_FAILED.response();
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return NamedProblem.NOT_FOUND.response();
  }

  /** 405 keeps the Allow header listing the methods the spec declares for the path (RFC 9110). */
  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return NamedProblem.METHOD_NOT_ALLOWED.response(headers);
  }

  /** 415 keeps the Accept header listing the media types the operation consumes. */
  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return NamedProblem.UNSUPPORTED_MEDIA_TYPE.response(headers);
  }

  /** ADR0019: bodies over 1 MB are refused before they are parsed. */
  @ExceptionHandler(RequestTooLarge.class)
  ResponseEntity<Object> handleRequestTooLarge() {
    return NamedProblem.PAYLOAD_TOO_LARGE.response();
  }

  /** The spec's own violation messages tell the client what to fix; they hold no internal data. */
  @ExceptionHandler(RequestValidationFailed.class)
  ResponseEntity<Object> handleRequestValidationFailed(RequestValidationFailed violations) {
    ResponseEntity<Object> response = NamedProblem.VALIDATION_FAILED.response();
    ((ProblemDetail) response.getBody()).setDetail(violations.getMessage());
    return response;
  }

  /** A domain rejection keeps its code and carries its rule id (error map of the OpenAPI spec). */
  @ExceptionHandler(DomainRejection.class)
  ResponseEntity<Object> handleDomainRejection(DomainRejection rejection) {
    HttpStatus status = statusOf(rejection.code());
    ProblemDetail problem = Problems.of(status, rejection.code(), rejection.getMessage());
    problem.setProperty("rule", rejection.rule());
    return ResponseEntity.status(status).body(problem);
  }

  /** Spec P11: actor rules 403, the request's state 409, every other rule 422. */
  private static HttpStatus statusOf(String code) {
    return switch (code) {
      case "OWN_REQUEST", "PROVIDER_NOT_ASKED" -> HttpStatus.FORBIDDEN;
      case "INVALID_STATE" -> HttpStatus.CONFLICT;
      default -> HttpStatus.UNPROCESSABLE_ENTITY;
    };
  }

  // ADR0010: the use case retried once; a second conflict reaches the caller
  @ExceptionHandler(ConcurrencyConflict.class)
  ResponseEntity<Object> handleConcurrencyConflict() {
    return NamedProblem.CONCURRENT_UPDATE.response();
  }

  @ExceptionHandler(HelpRequestNotFound.class)
  ResponseEntity<Object> handleNotFound() {
    return NamedProblem.NOT_FOUND.response();
  }

  @ExceptionHandler(NotAuthorized.class)
  ResponseEntity<Object> handleNotAuthorized() {
    return NamedProblem.NOT_AUTHORIZED.response();
  }

  @ExceptionHandler(NotImplemented.class)
  ResponseEntity<Object> handleNotImplemented() {
    return NamedProblem.NOT_IMPLEMENTED.response();
  }

  /** Anything unmapped: 500 without internal detail; the cause is logged, never returned. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception unexpected) {
    LOG.error("unexpected error", unexpected);
    return NamedProblem.INTERNAL_ERROR.response();
  }

  /**
   * An error the map does not name answers the nearest named entry: a server error is an internal
   * error, a bad request a validation failure; any other status names the code, its reason phrase
   * the title. The headers Spring set stay.
   */
  private static ResponseEntity<Object> unnamedError(
      HttpStatusCode statusCode, HttpHeaders headers) {
    HttpStatus status = HttpStatus.valueOf(statusCode.value());
    if (status.is5xxServerError()) {
      return NamedProblem.INTERNAL_ERROR.response(status, headers);
    }
    if (status == HttpStatus.BAD_REQUEST) {
      return NamedProblem.VALIDATION_FAILED.response(headers);
    }
    return Problems.response(status, status.name(), status.getReasonPhrase(), headers);
  }
}
