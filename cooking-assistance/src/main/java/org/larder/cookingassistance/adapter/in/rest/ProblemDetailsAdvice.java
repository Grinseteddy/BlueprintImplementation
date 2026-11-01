package org.larder.cookingassistance.adapter.in.rest;

import org.larder.cookingassistance.application.NotAuthorized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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

  @ExceptionHandler(RequestValidationFailed.class)
  ResponseEntity<Object> handleRequestValidationFailed() {
    return validationFailed();
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
    return ResponseEntity.status(status).body(Problems.of(status, code, title));
  }
}
