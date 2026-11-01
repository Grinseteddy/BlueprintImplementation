package org.larder;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Larder process with production configuration on PostgreSQL and RabbitMQ.
 */
class CompositionRootIT {

  @Disabled("step 12 test list")
  @DisplayName("B1 the service starts with production configuration; Actuator health and readiness UP -- B3-S01 US-CA-03 QS-14 ADR0020")
  @Test
  void serviceStartsAndReportsHealthAndReadiness() {}
}
