package org.larder.cookingassistance.domain.helprequest;

// INV-38: a picture link is at most 2048 characters (step 3 r26, security review SEC-24)
/** A picture link longer than 2048 characters. */
public final class PictureLinkTooLong extends DomainRejection {

  public PictureLinkTooLong() {
    super("INV-38", "INVALID_PICTURE_LINK", "A picture link is at most 2048 characters");
  }
}
