package org.larder.cookingassistance.adapter.in.rest;

import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.NotAuthorized;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.DomainRejection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** The error map of the API: RFC 9457 Problem Details with the codes of cooking-assistance.openapi.yaml. */
@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationFailed();
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationFailed();
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  /** 405 keeps the Allow header listing the methods the spec declares for the path (RFC 9110). */
  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problem(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "The method is not allowed for this resource",
        headers);
  }

  /** 415 keeps the Accept header listing the media types the operation consumes. */
  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return problem(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "UNSUPPORTED_MEDIA_TYPE",
        "The content type is not supported",
        headers);
  }

  @ExceptionHandler(RequestValidationFailed.class)
  ResponseEntity<Object> handleRequestValidationFailed() {
    return validationFailed();
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
    return problem(
        HttpStatus.CONFLICT, "CONCURRENT_UPDATE", "The help request was changed concurrently");
  }

  @ExceptionHandler(HelpRequestNotFound.class)
  ResponseEntity<Object> handleNotFound() {
    return problem(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
  }

  @ExceptionHandler(NotAuthorized.class)
  ResponseEntity<Object> handleNotAuthorized() {
    return problem(HttpStatus.FORBIDDEN, "NOT_AUTHORIZED", "The caller may not issue this command");
  }

  @ExceptionHandler(NotImplemented.class)
  ResponseEntity<Object> handleNotImplemented() {
    return problem(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "Not implemented yet");
  }

  /** Anything unmapped: 500 without internal detail; the cause is logged, never returned. */
  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> handleUnexpected(Exception unexpected) {
    LOG.error("unexpected error", unexpected);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
  }

  private static ResponseEntity<Object> validationFailed() {
    return problem(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request does not match the API contract");
  }

  private static ResponseEntity<Object> problem(HttpStatus status, String code, String title) {
    return problem(status, code, title, HttpHeaders.EMPTY);
  }

  private static ResponseEntity<Object> problem(
      HttpStatus status, String code, String title, HttpHeaders headers) {
    return ResponseEntity.status(status).headers(headers).body(Problems.of(status, code, title));
  }
}
