package org.larder.cookingassistance.domain.helprequest;

// INV-01: the request goes to the providers the cook chose; only they answer (brief, spec 403)
/** A provider answered who was not asked. */
public final class ProviderNotAsked extends DomainRejection {

  public ProviderNotAsked() {
    super("INV-01", "PROVIDER_NOT_ASKED", "Only the asked providers can answer");
  }
}
