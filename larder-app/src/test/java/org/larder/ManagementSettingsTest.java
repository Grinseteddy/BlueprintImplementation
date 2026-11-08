package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The health probes and metrics answer on their own port, never the API's (ADR0020, SEC-16). */
class ManagementSettingsTest {

  private static String managementPortWith(Map<String, Object> environment) throws Exception {
    return HostSettings.resolvedWith(environment).getProperty("management.server.port");
  }

  @DisplayName(
      "M1 LARDER_MANAGEMENT_PORT=9090 -> the management port is 9090; unset -> 8081 -- B3-S01 US-CA-03 OWASP-API8 ADR0020 SEC-16")
  @Test
  void managementPortFollowsTheEnvironment() throws Exception {
    assertThat(managementPortWith(Map.of("LARDER_MANAGEMENT_PORT", "9090")))
        .as("LARDER_MANAGEMENT_PORT=9090")
        .isEqualTo("9090");
    assertThat(managementPortWith(Map.of())).as("unset").isEqualTo("8081");
  }
}
