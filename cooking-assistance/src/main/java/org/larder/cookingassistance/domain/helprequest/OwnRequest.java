package org.larder.cookingassistance.domain.helprequest;

// INV-36: a cook never answers their own request (decision D3)
/** The requester tried to answer their own Help Request. */
public final class OwnRequest extends DomainRejection {

  public OwnRequest() {
    super("INV-36", "OWN_REQUEST", "A cook never answers their own request");
  }
}
