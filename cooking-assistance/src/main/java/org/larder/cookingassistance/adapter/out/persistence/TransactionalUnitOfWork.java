package org.larder.cookingassistance.adapter.out.persistence;

import java.util.function.Supplier;
import org.larder.cookingassistance.application.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit of work on the module's database transaction (ADR0015). */
@Component
class TransactionalUnitOfWork implements UnitOfWork {

  private final TransactionTemplate transaction;

  TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transaction = new TransactionTemplate(transactionManager);
  }

  @Override
  public <T> T transactionally(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }
}
