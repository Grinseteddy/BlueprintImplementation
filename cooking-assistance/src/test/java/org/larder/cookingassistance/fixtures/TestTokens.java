package org.larder.cookingassistance.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.larder.cookingassistance.domain.helprequest.CookId;

/**
 * Bearer tokens as the Larder identity provider issues them (ADR0012), signed with a key generated
 * for this test run; the resource server verifies them with the matching public key.
 */
public final class TestTokens {

  private static final RSAKey KEY = generateKey();

  private final String subject;
  private List<String> roles = List.of();
  private boolean withRoles = true;
  private String scope = "";
  private String issuer = "https://larder.org";
  private String audience = "cooking-assistance";
  private Instant expiresAt = Instant.now().plusSeconds(300);

  private TestTokens(String subject) {
    this.subject = subject;
  }

  /** The signing keys the resource server verifies this run's tokens with. */
  public static JWKSource<SecurityContext> signingKeys() {
    return new ImmutableJWKSet<>(new JWKSet(KEY.toPublicJWK()));
  }

  public static TestTokens forCook(CookId cook) {
    return new TestTokens(cook.value().toString());
  }

  public static TestTokens forSubject(String subject) {
    return new TestTokens(subject);
  }

  public TestTokens roles(String... roles) {
    this.roles = List.of(roles);
    return this;
  }

  /** A token that carries no roles claim at all. */
  public TestTokens withoutRoles() {
    this.withRoles = false;
    return this;
  }

  public TestTokens scope(String scope) {
    this.scope = scope;
    return this;
  }

  public TestTokens audience(String audience) {
    this.audience = audience;
    return this;
  }

  public TestTokens issuer(String issuer) {
    this.issuer = issuer;
    return this;
  }

  public TestTokens expiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  public String bearer() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .claim("roles", withRoles ? roles : null)
            .claim("premium", false)
            .claim("scope", scope)
            .issueTime(Date.from(expiresAt.minusSeconds(600)))
            .expirationTime(Date.from(expiresAt))
            .build();
    SignedJWT jwt =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    return "Bearer " + jwt.serialize();
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-run").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
