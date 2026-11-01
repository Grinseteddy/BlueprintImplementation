package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/** JWT resource server (ADR0012): a bearer-only, stateless API; refusals are Problem Details. */
@Configuration
class SecurityConfiguration {

  // ADR0012: tokens are issued by https://larder.org for the audience cooking-assistance
  private static final String ISSUER = "https://larder.org";
  private static final String AUDIENCE = "cooking-assistance";

  @Bean
  SecurityFilterChain api(HttpSecurity http, ObjectMapper json) throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // ADR0013: scopes per operation as the OpenAPI spec declares them
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(HttpMethod.POST, "/help-requests/**")
                    .hasAuthority("SCOPE_help:write")
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> {})
                    .authenticationEntryPoint(unauthenticated(json))
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
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(ISSUER),
            new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, audience -> audience != null && audience.contains(AUDIENCE)),
            // ADR0012: the subject is the cook id
            new JwtClaimValidator<String>(JwtClaimNames.SUB, SecurityConfiguration::isUuid)));
    return decoder;
  }

  private static boolean isUuid(String subject) {
    if (subject == null) {
      return false;
    }
    try {
      UUID.fromString(subject);
      return true;
    } catch (IllegalArgumentException notAUuid) {
      return false;
    }
  }

  /** 403 with the bearer challenge and a Problem body (code INSUFFICIENT_SCOPE). */
  private static AccessDeniedHandler insufficientScope(ObjectMapper json) {
    BearerTokenAccessDeniedHandler challenge = new BearerTokenAccessDeniedHandler();
    return (request, response, exception) -> {
      challenge.handle(request, response, exception);
      writeProblem(
          response,
          json,
          Problems.of(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "The token lacks the required scope"));
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
