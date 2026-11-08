package org.larder.cookingassistance.adapter.in.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** ADR0020, QS-14: the help request a request is about is on its log lines. */
@Component
class HelpRequestLogContext implements WebMvcConfigurer, HandlerInterceptor {

  private static final String HELP_REQUEST_ID = "helpRequestId";

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(this);
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    // the path variable {helpRequestId} of the spec's paths, once the handler is mapped
    if (request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)
            instanceof Map<?, ?> variables
        && variables.get(HELP_REQUEST_ID) instanceof String id) {
      MDC.put(HELP_REQUEST_ID, id);
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    MDC.remove(HELP_REQUEST_ID);
  }
}
