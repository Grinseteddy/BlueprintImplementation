package org.larder;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** The host refuses to start without the module's broker identity (ADR0012, ADR0019, SEC-05). */
@Configuration(proxyBeanMethods = false)
class BrokerEnvironmentCheck {

  static final List<String> REQUIRED_BROKER_VARIABLES =
      List.of("LARDER_BROKER_HOST", "LARDER_BROKER_USERNAME", "LARDER_BROKER_PASSWORD");

  BrokerEnvironmentCheck(Environment environment) {
    // Spring leaves an unresolved ${...} in place; without this the host would connect nowhere
    REQUIRED_BROKER_VARIABLES.forEach(environment::getRequiredProperty);
  }
}
