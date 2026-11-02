package org.larder.cookingassistance.adapter.in.rest;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;

/** The bearer tokens this module accepts (ADR0012): issuer, audience, timestamps, a cook id. */
final class AcceptedTokens {

  // ADR0012: tokens are issued by https://larder.org for the audience cooking-assistance
  private static final String ISSUER = "https://larder.org";
  private static final String AUDIENCE = "cooking-assistance";

  private AcceptedTokens() {}

  static OAuth2TokenValidator<Jwt> validator() {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(ISSUER),
        new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, audience -> audience != null && audience.contains(AUDIENCE)),
        // ADR0012: every token expires; the default timestamp check skips a missing exp
        new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
        // ADR0012: the subject is the cook id
        new JwtClaimValidator<String>(JwtClaimNames.SUB, AcceptedTokens::isUuid));
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
}
