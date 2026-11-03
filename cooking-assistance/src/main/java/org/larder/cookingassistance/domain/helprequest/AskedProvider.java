package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;

/** A provider a Help Request was sent to, with its own deadline and state. */
public record AskedProvider(HelpProviderType providerType, Instant deadline, ProviderState state) {

  public AskedProvider(HelpProviderType providerType, Instant deadline) {
    this(providerType, deadline, ProviderState.ASKED);
  }

  /** This side belongs to the given provider: one side per provider type. */
  boolean isFor(HelpProvider provider) {
    return providerType == provider.type();
  }

  /** This provider is asked again, with a new deadline (INV-06). */
  AskedProvider askedAgain(Instant newDeadline) {
    return new AskedProvider(providerType, newDeadline, ProviderState.ASKED);
  }

  /** This provider's side timed out: no answer by the deadline (INV-09). */
  AskedProvider timedOut() {
    return new AskedProvider(providerType, deadline, ProviderState.TIMED_OUT);
  }

  /** This provider's side has been answered. */
  AskedProvider answered() {
    return new AskedProvider(providerType, deadline, ProviderState.ANSWERED);
  }

  // INV-09: the deadline itself still counts; only a later instant has passed it (step 3 r5)
  boolean deadlinePassedAt(Instant instant) {
    return instant.isAfter(deadline);
  }
}
