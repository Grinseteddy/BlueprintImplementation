package org.larder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Health and readiness probes and the metrics of the process are open to the platform, on the
 * management port only (SEC-16); every module API stays protected.
 */
@Configuration
class ProbeSecurity {

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  SecurityFilterChain probes(HttpSecurity http) throws Exception {
    return http.securityMatcher("/actuator/health/**", "/actuator/prometheus")
        .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
        .build();
  }
}
