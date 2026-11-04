package org.larder.cookingassistance.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
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
  private final JdbcClient jdbc;

  JdbcHelpRequestRepository(HelpRequestRows rows, Outbox outbox, JdbcClient jdbc) {
    this.rows = rows;
    this.outbox = outbox;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<HelpRequest> findById(HelpRequestId id) {
    return rows.findById(id.value()).map(HelpRequestMapper::toDomain);
  }

  @Override
  public List<HelpRequest> findDueForTimeOut(Instant now, int limit) {
    return claimDueIds(now, limit).stream()
        .map(id -> rows.findById(id).map(HelpRequestMapper::toDomain).orElseThrow())
        .toList();
  }

  /**
   * ADR0021: locks the candidates in the caller's transaction; rows another poller holds are
   * skipped. The aggregate stays authoritative (INV-09).
   */
  private List<UUID> claimDueIds(Instant now, int limit) {
    // INV-09: the deadline itself still counts — a provider at its deadline is still waiting
    return jdbc.sql(
            """
            select h.id from help_request h
            where h.status = 'OPEN'
              and not exists (select 1 from asked_provider a
                              where a.help_request_id = h.id and a.deadline >= :now)
            order by h.id
            limit :limit
            for update of h skip locked""")
        .param("now", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
        .param("limit", limit)
        .query(UUID.class)
        .list();
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
