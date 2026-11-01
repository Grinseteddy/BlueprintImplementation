package org.larder.cookingassistance.adapter.out.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Fake broker behind the publisher port: records every confirmed message. */
class RecordingPublisher implements MessagePublisher {

  private final List<OutgoingMessage> published = new ArrayList<>();
  private boolean loseNextConfirm;
  private boolean rejectNext;
  private volatile CountDownLatch entered;
  private volatile CountDownLatch release;
  private volatile long delayMillis;

  @Override
  public void publish(OutgoingMessage message) {
    if (delayMillis > 0) {
      sleep(delayMillis);
    }
    CountDownLatch waitFor = release;
    if (waitFor != null) {
      release = null;
      entered.countDown();
      await(waitFor);
    }
    record(message);
  }

  private synchronized void record(OutgoingMessage message) {
    if (rejectNext) {
      rejectNext = false;
      throw new IllegalStateException("broker rejected the message");
    }
    published.add(message);
    if (loseNextConfirm) {
      loseNextConfirm = false;
      throw new IllegalStateException("confirm lost");
    }
  }

  /** Every publish takes this long, so concurrent relay runs overlap. */
  void delayEachPublish(long millis) {
    this.delayMillis = millis;
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** The next publish signals {@code entered}, then waits for {@code release} before confirming. */
  void blockNext(CountDownLatch entered, CountDownLatch release) {
    this.entered = entered;
    this.release = release;
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** The broker refuses the next message; nothing is published. */
  synchronized void rejectNext() {
    rejectNext = true;
  }

  /** The next message reaches the broker, but its confirm does not reach the relay. */
  synchronized void loseConfirmOfNext() {
    loseNextConfirm = true;
  }

  synchronized List<OutgoingMessage> published() {
    return List.copyOf(published);
  }
}
