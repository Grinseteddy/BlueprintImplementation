package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

/** The help request id on the log lines of a request (ADR0020, QS-14). */
class HelpRequestLogContextTest {

  final HelpRequestLogContext logContext = new HelpRequestLogContext();

  @AfterEach
  void clearTheContext() {
    MDC.clear();
  }

  private void requestFor(String helpRequestId) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("helpRequestId", helpRequestId));
    logContext.preHandle(request, new MockHttpServletResponse(), new Object());
  }

  @DisplayName(
      "L6 a {helpRequestId} that is not a UUID -> not on the log context: raw path text never reaches a log line -- B3-S03 US-CA-03 OWASP-A09 SEC-06")
  @Test
  void aPathThatIsNotAUuidStaysOutOfTheLog() {
    requestFor("x\n2026-11-08 ERROR forged line");

    assertThat(MDC.get("helpRequestId")).isNull();
  }

  @DisplayName(
      "L6 a {helpRequestId} that is a UUID -> on the log context -- B3-S03 US-CA-03 QS-14 ADR0020 SEC-06")
  @Test
  void aUuidGoesOnTheLogContext() {
    requestFor("23a8eeed-35f6-460b-892e-7bb458a8fded");

    assertThat(MDC.get("helpRequestId")).isEqualTo("23a8eeed-35f6-460b-892e-7bb458a8fded");
  }
}
