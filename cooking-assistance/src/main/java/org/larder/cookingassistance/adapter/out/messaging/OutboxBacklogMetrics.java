package org.larder.cookingassistance.adapter.out.messaging;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** QS-14: how many outbox rows wait to be published, and how long the oldest has waited. */
@Component
class OutboxBacklogMetrics implements MeterBinder {

  private final JdbcClient jdbc;
  private final Clock clock;

  OutboxBacklogMetrics(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("cooking_assistance.outbox.backlog", this, OutboxBacklogMetrics::backlog)
        .description("outbox rows neither published nor dead-lettered")
        .register(registry);
    Gauge.builder("cooking_assistance.outbox.oldest.age", this, OutboxBacklogMetrics::oldestAge)
        .description("age of the oldest outbox row neither published nor dead-lettered")
        .baseUnit("seconds")
        .register(registry);
  }

  private double backlog() {
    return jdbc.sql("select count(*) from outbox where " + OutboxRelay.PENDING)
        .query(Long.class)
        .single();
  }

  private double oldestAge() {
    return jdbc.sql("select min(occurred_at) from outbox where " + OutboxRelay.PENDING)
        .query(OffsetDateTime.class)
        .optional()
        .map(oldest -> Duration.between(oldest.toInstant(), clock.instant()).toMillis() / 1000.0)
        .orElse(0.0);
  }
}
