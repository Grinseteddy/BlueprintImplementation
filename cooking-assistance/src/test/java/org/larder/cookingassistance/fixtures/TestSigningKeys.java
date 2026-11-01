package org.larder.cookingassistance.fixtures;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** The resource server verifies tokens with this run's key instead of the identity provider's. */
@TestConfiguration
public class TestSigningKeys {

  @Bean
  @Primary
  JWKSource<SecurityContext> testSigningKeys() {
    return TestTokens.signingKeys();
  }
}
