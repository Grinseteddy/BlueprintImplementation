package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT resource server (ADR0012): a bearer-only, stateless API; refusals are Problem Details. */
@Configuration
class SecurityConfiguration {

  /** The paths of the read operations, which the spec guards with help:read. */
  private static final String[] READ_PATHS = {"/help-requests", "/help-requests/**", "/helps"};

  @Bean
  SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
    AuthenticationEntryPoint unauthenticated = unauthenticated(json);
    return http.csrf(AbstractHttpConfigurer::disable)
        // bearer only (ADR0012): Spring Security 6.5 accepts DPoP on its own (CVE-2026-41707)
        .addFilterBefore(refuseDPoP(unauthenticated), AuthenticationFilter.class)
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ADR0013: scopes per operation as the OpenAPI spec declares them
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.POST, "/help-requests/**")
                    .hasAuthority("SCOPE_help:write")
                    .requestMatchers(HttpMethod.GET, READ_PATHS)
                    .hasAuthority("SCOPE_help:read")
                    // Spring MVC serves HEAD with the GET handler: it reads like a GET (OWASP-API5)
                    .requestMatchers(HttpMethod.HEAD, READ_PATHS)
                    .hasAuthority("SCOPE_help:read")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> {})
                    .authenticationEntryPoint(unauthenticated)
                    .accessDeniedHandler(insufficientScope(json)))
        .build();
  }

  /** The identity provider's signing keys (ADR0012), fetched from its JWKS endpoint when needed. */
  @Bean
  JWKSource<SecurityContext> larderSigningKeys(
      @Value("${cooking-assistance.security.jwk-set-uri}") URL jwkSetUri) {
    return JWKSourceBuilder.create(jwkSetUri).build();
  }

  @Bean
  JwtDecoder jwtDecoder(JWKSource<SecurityContext> signingKeys) {
    DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
    processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, signingKeys));
    // claims are checked by the decoder's validators
    processor.setJWTClaimsSetVerifier((claims, context) -> {});
    NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
    decoder.setJwtValidator(AcceptedTokens.validator());
    return decoder;
  }

  /** 401 for a token presented with the DPoP scheme, before Spring's DPoP filter sees it. */
  private static OncePerRequestFilter refuseDPoP(AuthenticationEntryPoint unauthenticated) {
    return new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
          HttpServletRequest request, HttpServletResponse response, FilterChain chain)
          throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.regionMatches(true, 0, "DPoP ", 0, 5)) {
          unauthenticated.commence(
              request, response, new InvalidBearerTokenException("DPoP is not accepted"));
          return;
        }
        chain.doFilter(request, response);
      }
    };
  }

  /** 403 with the bearer challenge and a Problem body (code INSUFFICIENT_SCOPE). */
  private static AccessDeniedHandler insufficientScope(ObjectMapper json) {
    BearerTokenAccessDeniedHandler challenge = new BearerTokenAccessDeniedHandler();
    return (request, response, exception) -> {
      challenge.handle(request, response, exception);
      writeProblem(
          response,
          json,
          Problems.of(
              HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "The token lacks the required scope"));
    };
  }

  /** 401 with the bearer challenge and a Problem body (code UNAUTHENTICATED). */
  private static AuthenticationEntryPoint unauthenticated(ObjectMapper json) {
    BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
    return (request, response, exception) -> {
      challenge.commence(request, response, exception);
      writeProblem(
          response,
          json,
          Problems.of(
              HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication missing or invalid"));
    };
  }

  private static void writeProblem(
      HttpServletResponse response, ObjectMapper json, ProblemDetail problem) throws IOException {
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
