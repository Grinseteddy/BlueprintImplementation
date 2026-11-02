package org.larder.cookingassistance.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Refuses OPTIONS, which the approved OpenAPI spec declares for no path, with the methods the spec
 * declares for the requested path; Spring would otherwise answer it with its own handler.
 */
class OpenApiOptionsRefusal implements HandlerInterceptor {

  private final OpenApiContract contract;

  OpenApiOptionsRefusal(OpenApiContract contract) {
    this.contract = contract;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws HttpRequestMethodNotSupportedException {
    if (HttpMethod.OPTIONS.matches(request.getMethod())) {
      String path = request.getRequestURI().substring(request.getContextPath().length());
      throw new HttpRequestMethodNotSupportedException(
          request.getMethod(), contract.methodsDeclaredFor(path));
    }
    return true;
  }
}
