package org.larder.cookingassistance.fixtures;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The context's clock in integration tests that use the fixtures: it starts at story time (Friday
 * 18:10 CET, {@link HelpRequests#FRIDAY_18_10}) and ticks like the real one, so the fixtures'
 * deadlines hold on any day the tests run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class StoryClock {

  @Bean
  @Primary
  Clock storyClock() {
    return Clock.offset(
        Clock.systemUTC(), Duration.between(Instant.now(), HelpRequests.FRIDAY_18_10.instant()));
  }
}
