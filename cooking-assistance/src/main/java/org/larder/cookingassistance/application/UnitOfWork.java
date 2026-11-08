package org.larder.cookingassistance.application;

import java.util.function.Supplier;

/**
 * Runs work in one transaction: committed when the work returns, rolled back when it throws
 * (ADR0015 — a consumer writes its processed-message record and the Help Request together).
 */
public interface UnitOfWork {

  <T> T transactionally(Supplier<T> work);

  /**
   * Runs part of the current unit of work in a savepoint: when it throws, only its own writes are
   * undone and the unit of work goes on (ADR0021, one failing candidate of a batch). Without a
   * transaction of its own (the use-case fakes), the work just runs.
   */
  default <T> T inSavepoint(Supplier<T> work) {
    return work.get();
  }
}
