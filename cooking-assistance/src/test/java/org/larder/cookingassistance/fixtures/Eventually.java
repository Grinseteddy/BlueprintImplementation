package org.larder.cookingassistance.fixtures;

import java.time.Duration;
import java.time.Instant;

/** Polls an asynchronous outcome until it holds or a deadline passes; never a fixed sleep. */
public final class Eventually {

  private Eventually() {}

  /** Runs the assertion until it passes; after the deadline its last failure is thrown. */
  public static void within(Duration deadline, Runnable assertion) {
    Instant until = Instant.now().plus(deadline);
    while (true) {
      try {
        assertion.run();
        return;
      } catch (AssertionError notYet) {
        if (Instant.now().isAfter(until)) {
          throw notYet;
        }
        pause();
      }
    }
  }

  private static void pause() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
