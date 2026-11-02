package org.larder.cookingassistance.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Refuses a handler the approved OpenAPI spec declares no operation for (Spring's own /error,
 * SEC-09) as a resource that does not exist.
 */
class OpenApiUndeclaredHandlerRefusal implements HandlerInterceptor {

  private final OpenApiContract contract;

  OpenApiUndeclaredHandlerRefusal(OpenApiContract contract) {
    this.contract = contract;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws NoResourceFoundException {
    if (handler instanceof HandlerMethod method && !contract.declares(method.getMethod())) {
      throw new NoResourceFoundException(
          HttpMethod.valueOf(request.getMethod()), request.getRequestURI());
    }
    return true;
  }
}
