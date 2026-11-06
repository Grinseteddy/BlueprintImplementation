package org.larder.acceptance;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** The running service reads the acceptance clock, so scenarios can move to the story's time. */
@TestConfiguration(proxyBeanMethods = false)
public class AcceptanceTime {

  @Bean
  @Primary
  AcceptanceClock acceptanceClock() {
    return new AcceptanceClock();
  }
}
