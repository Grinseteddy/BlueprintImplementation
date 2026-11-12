package org.larder.cookingassistance.domain.helprequest;

import java.math.BigDecimal;

/** An ingredient to use instead, with its amount. */
public record SubstituteIngredient(String name, BigDecimal value, Unit unit) {

  boolean hasName() {
    return name != null && !name.isBlank();
  }

  /** INV-12 (step 3 r19, SEC-20): an amount a kitchen can use, and one that stays readable. */
  static final BigDecimal MAXIMUM_AMOUNT = new BigDecimal("10000");

  private static final int MAXIMUM_DECIMAL_PLACES = 3;

  /**
   * A positive amount of at most 10,000 with at most 3 decimal places in a unit (INV-12: a mix with
   * amounts).
   */
  boolean hasAmount() {
    return value != null
        && value.signum() > 0
        && value.compareTo(MAXIMUM_AMOUNT) <= 0
        && value.stripTrailingZeros().scale() <= MAXIMUM_DECIMAL_PLACES
        && unit != null;
  }
}
