package org.larder.cookingassistance.domain.helprequest;

// INV-01: a help request is sent to 1..2 preferred providers
/** The cook chose no provider for the help request. */
public final class NoHelpProviderChosen extends DomainRejection {

  public NoHelpProviderChosen() {
    super("INV-01", "VALIDATION_FAILED", "Choose at least one help provider");
  }
}
