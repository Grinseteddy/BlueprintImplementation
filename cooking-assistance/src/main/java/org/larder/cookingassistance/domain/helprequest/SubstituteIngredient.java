package org.larder.cookingassistance.domain.helprequest;

import java.math.BigDecimal;

/** An ingredient to use instead, with its amount. */
public record SubstituteIngredient(String name, BigDecimal value, Unit unit) {

  boolean hasName() {
    return name != null && !name.isBlank();
  }

  /** A positive amount in a unit (INV-12: a mix with amounts). */
  boolean hasAmount() {
    return value != null && value.signum() > 0 && unit != null;
  }
}
