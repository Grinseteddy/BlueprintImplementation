package org.larder.cookingassistance.application.fakes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.ConcurrencyConflict;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.Help;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestEvent;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/**
 * In-memory HelpRequestRepository for use-case tests; passes HelpRequestRepositoryContract. Stores
 * snapshots and rebuilds through reconstitute, never handing out the stored object.
 */
public class InMemoryHelpRequestRepository implements HelpRequestRepository {

  private static final Runnable NO_OTHER_WRITER = () -> {};

  private final Map<HelpRequestId, Snapshot> snapshots = new HashMap<>();
  private final List<HelpRequestEvent> recordedEvents = new ArrayList<>();
  private Runnable interleaved = NO_OTHER_WRITER;

  @Override
  public Optional<HelpRequest> findById(HelpRequestId id) {
    return Optional.ofNullable(snapshots.get(id)).map(Snapshot::reconstitute);
  }

  @Override
  public void save(HelpRequest helpRequest) {
    letOtherWriterSaveFirst();
    Snapshot stored = snapshots.get(helpRequest.id());
    long storedVersion = stored == null ? 0L : stored.version();
    if (storedVersion != helpRequest.version()) {
      throw new ConcurrencyConflict(helpRequest.id());
    }
    snapshots.put(helpRequest.id(), Snapshot.of(helpRequest, helpRequest.version() + 1));
    recordedEvents.addAll(helpRequest.pullEvents());
  }

  private void letOtherWriterSaveFirst() {
    Runnable otherWriter = interleaved;
    interleaved = NO_OTHER_WRITER;
    otherWriter.run();
  }

  /**
   * Test hook: runs {@code otherWriter} once inside the next save, before its version check, to
   * simulate a concurrent writer.
   */
  public void interleave(Runnable otherWriter) {
    this.interleaved = otherWriter;
  }

  /** Forgets every stored request and recorded event, for a fake shared by several tests. */
  public void clear() {
    snapshots.clear();
    recordedEvents.clear();
  }

  /** Events recorded with saved requests, in order (the fake's stand-in for the outbox). */
  public List<HelpRequestEvent> recordedEvents() {
    return List.copyOf(recordedEvents);
  }

  private record Snapshot(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      List<AskedProvider> askedProviders,
      HelpRequestStatus status,
      List<Help> helps,
      long version) {

    static Snapshot of(HelpRequest request, long storedVersion) {
      return new Snapshot(
          request.id(),
          request.requester(),
          request.title(),
          request.description(),
          request.type(),
          request.recipe(),
          request.howToStep(),
          List.copyOf(request.askedProviders()),
          request.status(),
          List.copyOf(request.helps()),
          storedVersion);
    }

    HelpRequest reconstitute() {
      return HelpRequest.reconstitute(
          id, requester, title, description, type, recipe, howToStep, askedProviders, status,
          helps, version);
    }
  }
}
