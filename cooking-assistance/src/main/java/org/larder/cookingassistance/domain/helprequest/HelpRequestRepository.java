package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Port to the stored Help Requests. {@code save} writes the aggregate and appends its pending
 * events to the outbox in one transaction (ADR0015); a stale version fails with {@link
 * ConcurrencyConflict} (ADR0010).
 */
public interface HelpRequestRepository {

  Optional<HelpRequest> findById(HelpRequestId id);

  /**
   * ADR0021: up to {@code limit} Open help requests whose every asked provider's deadline has
   * passed at {@code now} — candidates only, the aggregate decides (INV-09). Called inside a unit
   * of work, which holds the claimed rows until it commits.
   */
  List<HelpRequest> findDueForTimeOut(Instant now, int limit);

  void save(HelpRequest helpRequest);
}
