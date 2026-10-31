package org.larder.cookingassistance.application.fakes;

import java.util.function.Supplier;
import org.larder.cookingassistance.application.UnitOfWork;

/**
 * Unit of work for use-case tests: runs the work and tells whether a transaction is active.
 * Commit only — rollback is proven on the real unit of work (UnitOfWorkIT).
 */
public class RecordingUnitOfWork implements UnitOfWork {

  private boolean active;

  @Override
  public <T> T transactionally(Supplier<T> work) {
    active = true;
    try {
      return work.get();
    } finally {
      active = false;
    }
  }

  public boolean isActive() {
    return active;
  }
}
