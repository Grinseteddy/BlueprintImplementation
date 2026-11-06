package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The host's time-out poller settings come from the environment (ADR0019, ADR0021). */
class TimeOutPollerSettingsTest {

  /** The property TimeOutPolling is conditional on (cooking-assistance module). */
  static final String ENABLED = "cooking-assistance.timeout.poller.enabled";

  /** The delay TimeOutPolling's @Scheduled reads (cooking-assistance module). */
  static final String POLL_DELAY = "cooking-assistance.timeout.poll-delay";

  @DisplayName(
      "H1 LARDER_TIMEOUT_POLLER_ENABLED=false -> no time-out poller runs; unset -> it runs -- B3-S06 US-CA-03 ADR0021 ADR0019")
  @Test
  void pollerFollowsTheEnvironment() throws Exception {
    assertThat(
            HostSettings.resolvedWith(Map.of("LARDER_TIMEOUT_POLLER_ENABLED", "false"))
                .getProperty(ENABLED))
        .as("LARDER_TIMEOUT_POLLER_ENABLED=false")
        .isEqualTo("false");
    assertThat(HostSettings.resolvedWith(Map.of()).getProperty(ENABLED))
        .as("unset")
        .isEqualTo("true");
  }

  @DisplayName(
      "H2 LARDER_TIMEOUT_POLL_DELAY=250ms -> the poller waits 250 ms between runs; unset -> 10 s -- B3-S06 US-CA-03 ADR0021 ADR0019")
  @Test
  void pollDelayFollowsTheEnvironment() throws Exception {
    assertThat(
            HostSettings.resolvedWith(Map.of("LARDER_TIMEOUT_POLL_DELAY", "250ms"))
                .getProperty(POLL_DELAY))
        .as("LARDER_TIMEOUT_POLL_DELAY=250ms")
        .isEqualTo("250ms");
    assertThat(HostSettings.resolvedWith(Map.of()).getProperty(POLL_DELAY))
        .as("unset")
        .isEqualTo("10s");
  }
}
