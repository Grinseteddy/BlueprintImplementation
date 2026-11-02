package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The identity provider's keys and the public base URL come from the environment only. */
class EndpointSettingsTest {

  @DisplayName(
      "no LARDER_JWKS_URI and no LARDER_PUBLIC_BASE_URL -> the host does not start, naming LARDER_JWKS_URI -- B3-S01 US-CA-03 OWASP-API8 ADR0012 ADR0019")
  @Test
  void hostWithoutEndpointEnvironmentDoesNotStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(EndpointEnvironmentCheck.class)
        .run(
            context ->
                assertThat(context.getStartupFailure()).hasStackTraceContaining("LARDER_JWKS_URI"));
  }

  @DisplayName(
      "LARDER_JWKS_URI set, LARDER_PUBLIC_BASE_URL blank -> the host does not start, naming LARDER_PUBLIC_BASE_URL -- B3-S01 US-CA-03 OWASP-API8 SEC-10")
  @Test
  void hostWithBlankPublicBaseUrlDoesNotStart() {
    new ApplicationContextRunner()
        .withUserConfiguration(EndpointEnvironmentCheck.class)
        .withPropertyValues(
            "LARDER_JWKS_URI=https://larder.org/.well-known/jwks.json", "LARDER_PUBLIC_BASE_URL= ")
        .run(
            context ->
                assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("LARDER_PUBLIC_BASE_URL"));
  }
}
