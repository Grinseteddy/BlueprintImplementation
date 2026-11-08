package org.larder.cookingassistance.adapter.out.persistence;

import java.util.function.Supplier;
import org.larder.cookingassistance.application.UnitOfWork;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Unit of work on the module's database transaction (ADR0015). */
@Component
class TransactionalUnitOfWork implements UnitOfWork {

  private final TransactionTemplate transaction;
  private final TransactionTemplate savepoint;

  TransactionalUnitOfWork(PlatformTransactionManager transactionManager) {
    this.transaction = new TransactionTemplate(transactionManager);
    // ADR0021: part of a unit of work that fails alone (one candidate of a batch)
    this.savepoint = new TransactionTemplate(transactionManager);
    this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
  }

  @Override
  public <T> T transactionally(Supplier<T> work) {
    return transaction.execute(status -> work.get());
  }

  @Override
  public <T> T inSavepoint(Supplier<T> work) {
    return savepoint.execute(status -> work.get());
  }
}
