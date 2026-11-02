package org.larder.cookingassistance.adapter.in.rest;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a token presented with the DPoP scheme, before Spring's DPoP filter sees it: the API is
 * bearer only (ADR0012), and Spring Security 6.5 accepts DPoP on its own (CVE-2026-41707).
 */
class DPoPSchemeRefusal extends OncePerRequestFilter {

  private static final String DPOP_SCHEME = "DPoP ";

  private final AuthenticationEntryPoint unauthenticated;

  DPoPSchemeRefusal(AuthenticationEntryPoint unauthenticated) {
    this.unauthenticated = unauthenticated;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (presentsDPoP(request)) {
      unauthenticated.commence(
          request, response, new InvalidBearerTokenException("DPoP is not accepted"));
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean presentsDPoP(HttpServletRequest request) {
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    return authorization != null
        && authorization.regionMatches(true, 0, DPOP_SCHEME, 0, DPOP_SCHEME.length());
  }
}
