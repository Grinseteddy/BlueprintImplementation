package org.larder.cookingassistance.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.OAuth2ProtectedResourceMetadata;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/** JWT resource server (ADR0012): a bearer-only, stateless API; refusals are Problem Details. */
@Configuration
class SecurityConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);
  private static final int LOGGED_PATH_LENGTH = 200;

  /** The paths of the read operations, which the spec guards with help:read. */
  private static final String[] READ_PATHS = {"/help-requests", "/help-requests/**", "/helps"};

  @Bean
  SecurityFilterChain api(HttpSecurity http, ObjectMapper json, @PublicBaseUrl String publicBaseUrl)
      throws Exception {
    return http.csrf(AbstractHttpConfigurer::disable)
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
                    .protectedResourceMetadata(
                        metadata ->
                            metadata.protectedResourceMetadataCustomizer(
                                publishedResource(publicBaseUrl)))
                    .authenticationEntryPoint(unauthenticated(json, publicBaseUrl))
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

  /**
   * SEC-15: Spring serves this document; it names the configured resource, never the request's
   * Host, and claims no token binding the service does not verify.
   */
  private static Consumer<OAuth2ProtectedResourceMetadata.Builder> publishedResource(
      String publicBaseUrl) {
    return document ->
        document.resource(publicBaseUrl).tlsClientCertificateBoundAccessTokens(false);
  }

  /** 403 with the bearer challenge and a Problem body (code INSUFFICIENT_SCOPE). */
  private static AccessDeniedHandler insufficientScope(ObjectMapper json) {
    BearerTokenAccessDeniedHandler challenge = new BearerTokenAccessDeniedHandler();
    return (request, response, exception) -> {
      challenge.handle(request, response, exception);
      LOG.warn("Refused {} {}: 403 INSUFFICIENT_SCOPE", request.getMethod(), loggablePath(request));
      writeProblem(
          response,
          json,
          Problems.of(
              HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "The token lacks the required scope"));
    };
  }

  /** 401 with the bearer challenge and a Problem body (code UNAUTHENTICATED). */
  private static AuthenticationEntryPoint unauthenticated(ObjectMapper json, String publicBaseUrl) {
    BearerTokenAuthenticationEntryPoint challenge = new BearerTokenAuthenticationEntryPoint();
    // SEC-15: Spring's default builds this URL from the request's Host
    challenge.setResourceMetadataParameterResolver(
        request -> publicBaseUrl + "/.well-known/oauth-protected-resource");
    return (request, response, exception) -> {
      challenge.commence(request, response, exception);
      LOG.warn("Refused {} {}: 401 UNAUTHENTICATED", request.getMethod(), loggablePath(request));
      writeProblem(
          response,
          json,
          Problems.of(
              HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication missing or invalid"));
    };
  }

  /** SEC-19: a sender cannot size the security log; the URI is still percent-encoded (SEC-06). */
  private static String loggablePath(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path.substring(0, Math.min(path.length(), LOGGED_PATH_LENGTH));
  }

  private static void writeProblem(
      HttpServletResponse response, ObjectMapper json, ProblemDetail problem) throws IOException {
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
