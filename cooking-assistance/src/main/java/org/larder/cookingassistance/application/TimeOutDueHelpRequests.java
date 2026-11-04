package org.larder.cookingassistance.application;

import java.time.Clock;
import java.util.List;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

// cmd: Time out help request · agg: Help Request · slice: B3-S06, story: US-CA-03
/** Use case: the clock times out every due help request (policy Timeout, ADR0021). */
public class TimeOutDueHelpRequests {

  private final HelpRequestRepository helpRequests;
  private final UnitOfWork unitOfWork;
  private final Clock clock;
  private final int batchSize;

  public TimeOutDueHelpRequests(
      HelpRequestRepository helpRequests, UnitOfWork unitOfWork, Clock clock, int batchSize) {
    this.helpRequests = helpRequests;
    this.unitOfWork = unitOfWork;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  /** Times out the due help requests of one batch; returns how many were timed out. */
  public int execute() {
    // ADR0021: the claim's row locks hold until the saves commit
    return unitOfWork.transactionally(this::timeOutOneBatch);
  }

  private int timeOutOneBatch() {
    List<HelpRequest> due = helpRequests.findDueForTimeOut(clock.instant(), batchSize);
    for (HelpRequest request : due) {
      request.timeOut(clock);
      helpRequests.save(request);
    }
    return due.size();
  }
}
