package org.larder.cookingassistance.application.fakes;

import org.larder.cookingassistance.application.contract.HelpRequestRepositoryContract;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

/** Runs the repository contract against the in-memory fake (fast). */
class InMemoryHelpRequestRepositoryTest extends HelpRequestRepositoryContract {

  private final InMemoryHelpRequestRepository repository = new InMemoryHelpRequestRepository();

  @Override
  protected HelpRequestRepository repository() {
    return repository;
  }

  @Override
  protected int recordedEventCount() {
    return repository.recordedEvents().size();
  }
}
