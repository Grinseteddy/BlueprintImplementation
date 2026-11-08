package org.larder.cookingassistance.application;

import java.time.Clock;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;

// cmd: Time out help request · agg: Help Request · slice: B3-S06, story: US-CA-03
/** Use case: the clock times out every due help request (policy Timeout, ADR0021). */
public class TimeOutDueHelpRequests {

  private static final System.Logger LOG = System.getLogger(TimeOutDueHelpRequests.class.getName());

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

  /**
   * Whether to claim again after a run that timed out this many: only a full batch shows that the
   * claim stopped at its limit (ADR0021). A run with a failing candidate answers false even when
   * more are due; the next poll claims those.
   */
  public boolean shouldClaimAgainAfter(int timedOut) {
    return timedOut == batchSize;
  }

  private int timeOutOneBatch() {
    int timedOut = 0;
    for (HelpRequest request : helpRequests.findDueForTimeOut(clock.instant(), batchSize)) {
      if (timeOutAlone(request)) {
        timedOut++;
      }
    }
    return timedOut;
  }

  /** Whether this candidate was timed out. */
  private boolean timeOutAlone(HelpRequest request) {
    // ADR0021: one candidate fails alone; its savepoint is undone, the batch goes on
    try {
      unitOfWork.inSavepoint(
          () -> {
            request.timeOut(clock);
            helpRequests.save(request);
            return null;
          });
      return true;
    } catch (RuntimeException failure) {
      LOG.log(
          System.Logger.Level.ERROR,
          "Help request {0} not timed out: {1}",
          request.id().value(),
          failure.getMessage());
      return false;
    }
  }
}
