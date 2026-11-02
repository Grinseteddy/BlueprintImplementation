package org.larder.cookingassistance.domain.helprequest;

// INV-38: a picture is an https link (security review SEC-07)
/** A picture link that is not an https URL. */
public final class PictureLinkNotSecure extends DomainRejection {

  public PictureLinkNotSecure() {
    super("INV-38", "INVALID_PICTURE_LINK", "A picture is an https link");
  }
}
