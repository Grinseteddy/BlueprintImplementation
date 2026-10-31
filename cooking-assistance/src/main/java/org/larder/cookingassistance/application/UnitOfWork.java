package org.larder.cookingassistance.application;

import java.util.function.Supplier;

/**
 * Runs work in one transaction: committed when the work returns, rolled back when it throws
 * (ADR0015 — a consumer writes its processed-message record and the Help Request together).
 */
public interface UnitOfWork {

  <T> T transactionally(Supplier<T> work);
}
