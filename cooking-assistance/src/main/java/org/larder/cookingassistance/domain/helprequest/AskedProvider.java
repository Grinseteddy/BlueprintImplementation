package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;

/** A provider a Help Request was sent to, with its own deadline and state. */
public record AskedProvider(HelpProviderType providerType, Instant deadline, ProviderState state) {

  public AskedProvider(HelpProviderType providerType, Instant deadline) {
    this(providerType, deadline, ProviderState.ASKED);
  }

  /** This provider's side has been answered. */
  AskedProvider answered() {
    return new AskedProvider(providerType, deadline, ProviderState.ANSWERED);
  }
}
