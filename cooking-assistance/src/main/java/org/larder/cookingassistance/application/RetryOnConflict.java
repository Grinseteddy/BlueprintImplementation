package org.larder.cookingassistance.application;

import java.util.function.Supplier;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;

/** ADR0010: when another writer saved first, reload and retry the attempt once. */
final class RetryOnConflict {

  private RetryOnConflict() {}

  static <T> T once(Supplier<T> attempt) {
    try {
      return attempt.get();
    } catch (ConcurrencyConflict conflict) {
      return attempt.get();
    }
  }

  static void once(Runnable attempt) {
    once(
        () -> {
          attempt.run();
          return null;
        });
  }
}
