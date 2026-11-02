package org.larder;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** The host refuses to start without the module's database identity (ADR0009, ADR0019). */
@Configuration(proxyBeanMethods = false)
class DatabaseEnvironmentCheck {

  DatabaseEnvironmentCheck(Environment environment) {
    RequiredEnvironment.requireEach(
        environment, "LARDER_DB_URL", "LARDER_DB_USERNAME", "LARDER_DB_PASSWORD");
  }
}
