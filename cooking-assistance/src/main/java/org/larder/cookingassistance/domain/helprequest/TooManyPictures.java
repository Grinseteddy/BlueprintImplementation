package org.larder.cookingassistance.domain.helprequest;

// INV-38: a request or an answer carries at most 10 pictures
/** More than 10 pictures were attached. */
public final class TooManyPictures extends DomainRejection {

  public TooManyPictures() {
    super("INV-38", "TOO_MANY_PICTURES", "At most 10 pictures");
  }
}
