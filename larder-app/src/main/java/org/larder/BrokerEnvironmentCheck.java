package org.larder;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** The host refuses to start without the module's broker identity (ADR0012, ADR0019, SEC-05). */
@Configuration(proxyBeanMethods = false)
class BrokerEnvironmentCheck {

  BrokerEnvironmentCheck(Environment environment) {
    RequiredEnvironment.requireEach(
        environment, "LARDER_BROKER_HOST", "LARDER_BROKER_USERNAME", "LARDER_BROKER_PASSWORD");
  }
}
