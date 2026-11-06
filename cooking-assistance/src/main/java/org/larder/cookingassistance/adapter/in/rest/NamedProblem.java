package org.larder.cookingassistance.adapter.in.rest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * The error map's entries for failures that are not domain rejections (a domain rejection keeps the
 * code the domain gives it): code, status and title per cooking-assistance.openapi.yaml.
 */
enum NamedProblem {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request does not match the API contract"),
  NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
  PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The request body is larger than 1 MB"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The method is not allowed for this resource"),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The content type is not supported"),
  NOT_AUTHORIZED(HttpStatus.FORBIDDEN, "The caller may not issue this command"),
  NOT_REQUESTER(HttpStatus.FORBIDDEN, "Only the requester may issue this command"),
  CONCURRENT_UPDATE(HttpStatus.CONFLICT, "The help request was changed concurrently"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error"),
  NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "Not implemented yet"),
  SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Service unavailable");

  private final HttpStatus status;
  private final String title;

  NamedProblem(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  ResponseEntity<Object> response() {
    return response(HttpHeaders.EMPTY);
  }

  ResponseEntity<Object> response(HttpHeaders headers) {
    return response(status, headers);
  }

  /** The entry under another status of its class, e.g. a framework 503 as an internal error. */
  ResponseEntity<Object> response(HttpStatus actual, HttpHeaders headers) {
    return Problems.response(actual, name(), title, headers);
  }
}
