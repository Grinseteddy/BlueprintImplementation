package org.larder.cookingassistance.fixtures;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
  // fixed, so the same token is signed every time (a DPoP proof hashes it)
  private final Instant issuedAt = Instant.now().minusSeconds(300);
  private ECKey proofKey;

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

  /** A token that carries no exp claim at all. */
  public TestTokens withoutExpiry() {
    this.expiresAt = null;
    return this;
  }

  /** A DPoP-bound token (RFC 9449): its cnf.jkt names the key that signs the client's proofs. */
  public TestTokens boundTo(ECKey proofKey) {
    this.proofKey = proofKey;
    return this;
  }

  public static ECKey proofKey() {
    try {
      return new ECKeyGenerator(Curve.P_256).generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  public String bearer() {
    return "Bearer " + token();
  }

  /** The token presented with the DPoP scheme instead of Bearer. */
  public String dPoP() {
    return "DPoP " + token();
  }

  /** A valid DPoP proof (RFC 9449) for this token, signed with its bound key. */
  public String dPoPProof(String method, String url) {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .jwtID(UUID.randomUUID().toString())
            .claim("htm", method)
            .claim("htu", url)
            .issueTime(new Date())
            .claim("ath", sha256(token()).toString())
            .build();
    SignedJWT proof =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.ES256)
                .type(new JOSEObjectType("dpop+jwt"))
                .jwk(proofKey.toPublicJWK())
                .build(),
            claims);
    try {
      proof.sign(new ECDSASigner(proofKey));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    return proof.serialize();
  }

  private String token() {
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .claim("roles", withRoles ? roles : null)
            .claim("premium", false)
            .claim("scope", scope)
            .issueTime(Date.from(issuedAt))
            .expirationTime(expiresAt == null ? null : Date.from(expiresAt))
            .claim("cnf", proofKey == null ? null : Map.of("jkt", thumbprint(proofKey)))
            .build();
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    try {
      jwt.sign(new RSASSASigner(KEY));
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
    return jwt.serialize();
  }

  private static String thumbprint(ECKey key) {
    try {
      return key.computeThumbprint().toString();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Base64URL sha256(String value) {
    try {
      return Base64URL.encode(
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static RSAKey generateKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("test-run").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }
}
