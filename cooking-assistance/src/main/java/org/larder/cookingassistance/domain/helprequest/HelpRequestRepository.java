package org.larder.cookingassistance.domain.helprequest;

import java.util.Optional;

/**
 * Port to the stored Help Requests. {@code save} writes the aggregate and appends its pending
 * events to the outbox in one transaction (ADR0015); a stale version fails with {@link
 * ConcurrencyConflict} (ADR0010).
 */
public interface HelpRequestRepository {

  Optional<HelpRequest> findById(HelpRequestId id);

  void save(HelpRequest helpRequest);
}
