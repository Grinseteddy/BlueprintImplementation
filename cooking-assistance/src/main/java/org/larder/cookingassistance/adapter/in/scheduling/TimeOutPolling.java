package org.larder.cookingassistance.adapter.in.scheduling;

import org.larder.cookingassistance.application.TimeOutDueHelpRequests;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Polls for due help requests and times them out (ADR0021). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cooking-assistance.timeout.poller.enabled", matchIfMissing = true)
class TimeOutPolling {

  private final TimeOutDueHelpRequests timeOut;

  TimeOutPolling(TimeOutDueHelpRequests timeOut) {
    this.timeOut = timeOut;
  }

  @Scheduled(fixedDelayString = "${cooking-assistance.timeout.poll-delay:10s}")
  void poll() {
    // F5: drain in the same poll while more may be due (QS-09)
    int timedOut;
    do {
      timedOut = timeOut.execute();
    } while (timeOut.moreMayBeDueAfter(timedOut));
  }
}
