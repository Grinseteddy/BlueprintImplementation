package org.larder;

import org.springframework.core.env.Environment;

/**
 * Secrets and endpoints come only from environment variables; a missing or blank one stops the host
 * (ADR0019).
 */
final class RequiredEnvironment {

  private RequiredEnvironment() {}

  static void requireEach(Environment environment, String... variables) {
    // Spring leaves an unresolved ${...} in place; without this the host would connect nowhere
    for (String variable : variables) {
      if (environment.getRequiredProperty(variable).isBlank()) {
        // e.g. an empty broker host resolves to localhost (security re-test SEC-13)
        throw new IllegalStateException(variable + " must not be blank");
      }
    }
  }
}
