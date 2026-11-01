package org.larder.cookingassistance.adapter.out.persistence;

import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Help Request repository on PostgreSQL with Spring Data JDBC (ADR0009) and an outbox (ADR0015).
 */
@Repository
class JdbcHelpRequestRepository implements HelpRequestRepository {

  private static final String AGGREGATE_TYPE = "HelpRequest";

  private final HelpRequestRows rows;
  private final Outbox outbox;

  JdbcHelpRequestRepository(HelpRequestRows rows, Outbox outbox) {
    this.rows = rows;
    this.outbox = outbox;
  }

  @Override
  public Optional<HelpRequest> findById(HelpRequestId id) {
    return rows.findById(id.value()).map(HelpRequestMapper::toDomain);
  }

  @Override
  @Transactional
  public void save(HelpRequest helpRequest) {
    try {
      rows.save(HelpRequestMapper.toRow(helpRequest));
    } catch (OptimisticLockingFailureException e) {
      throw new ConcurrencyConflict(helpRequest.id());
    }
    for (HelpRequestEvent event : helpRequest.pullEvents()) {
      outbox.append(
          AGGREGATE_TYPE, helpRequest.id().value(), OutboxEventMapper.toOutboxEvent(event));
    }
  }
}
