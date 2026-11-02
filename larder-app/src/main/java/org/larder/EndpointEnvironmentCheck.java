package org.larder;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** The host refuses to start without the endpoints it trusts and publishes (ADR0012, SEC-10). */
@Configuration(proxyBeanMethods = false)
class EndpointEnvironmentCheck {

  EndpointEnvironmentCheck(Environment environment) {
    RequiredEnvironment.requireEach(environment, "LARDER_JWKS_URI", "LARDER_PUBLIC_BASE_URL");
  }
}
