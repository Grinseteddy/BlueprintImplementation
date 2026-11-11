package org.larder.cookingassistance.domain.helprequest;

import java.math.BigDecimal;

/** An ingredient to use instead, with its amount. */
public record SubstituteIngredient(String name, BigDecimal value, Unit unit) {

  boolean hasName() {
    return name != null && !name.isBlank();
  }
}
