package org.larder;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What the host prints next to every log line (ADR0020, QS-14). */
class LogSettingsTest {

  @DisplayName(
      "M4 every log line shows the trace id, the span id and the help request id from the log context -- B3-S01 US-CA-03 ADR0020 QS-14")
  @Test
  void logLinesShowTraceAndHelpRequest() throws Exception {
    assertThat(HostSettings.resolvedWith(Map.of()).getProperty("logging.pattern.correlation"))
        .contains("%X{traceId", "%X{spanId", "%X{helpRequestId");
  }
}
