package org.larder.cookingassistance.domain.helprequest;

/** A command violated a domain rule; carries the rule ID and the API error code. */
public abstract class DomainRejection extends RuntimeException {

  private final String rule;
  private final String code;

  protected DomainRejection(String rule, String code, String message) {
    super(message);
    this.rule = rule;
    this.code = code;
  }

  public String rule() {
    return rule;
  }

  public String code() {
    return code;
  }
}
