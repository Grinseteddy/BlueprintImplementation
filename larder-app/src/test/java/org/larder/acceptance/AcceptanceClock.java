package org.larder.acceptance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The service's clock in the acceptance run: it ticks like the real one, at an offset the steps
 * move to the story's time (Friday 18:00, Saturday 06:00, next month).
 */
public class AcceptanceClock extends Clock {

  private final AtomicReference<Duration> offset = new AtomicReference<>(Duration.ZERO);

  /** From now on, the clock reads the given instant and ticks on from there. */
  public void moveTo(Instant storyTime) {
    offset.set(Duration.between(Instant.now(), storyTime));
  }

  @Override
  public Instant instant() {
    return Instant.now().plus(offset.get());
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("the service reads time in UTC");
  }
}
