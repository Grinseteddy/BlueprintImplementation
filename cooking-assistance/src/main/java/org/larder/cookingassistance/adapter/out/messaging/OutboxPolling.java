package org.larder.cookingassistance.adapter.out.messaging;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs the outbox relay at a fixed delay (ADR0015): a run never overlaps the previous one in this
 * instance; several instances are safe through the relay's row locks.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cooking-assistance.outbox.poller.enabled", matchIfMissing = true)
class OutboxPolling {

  private final OutboxRelay relay;

  OutboxPolling(
      JdbcClient jdbc, TransactionTemplate transactions, MessagePublisher publisher, Clock clock) {
    this.relay = new OutboxRelay(jdbc, transactions, publisher, clock);
  }

  @Scheduled(fixedDelayString = "${cooking-assistance.outbox.poll-delay:1s}")
  void poll() {
    relay.runOnce();
  }
}
