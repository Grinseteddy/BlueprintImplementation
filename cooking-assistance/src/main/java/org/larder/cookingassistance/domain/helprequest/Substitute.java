package org.larder.cookingassistance.domain.helprequest;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** One proposed replacement for the requested ingredient: one ingredient or a mix with amounts. */
public record Substitute(
    IngredientId ingredient, List<SubstituteIngredient> substituteIngredients) {

  /** At least one ingredient, and no blank name among them (INV-12, step 3 r13). */
  boolean namesItsIngredients() {
    return !substituteIngredients.isEmpty()
        && substituteIngredients.stream().allMatch(SubstituteIngredient::hasName);
  }

  /**
   * The same substitute: the same ingredient names, whatever the amounts, case or outer space
   * (INV-15, S3).
   */
  boolean isSameAs(Substitute other) {
    return names().equals(other.names());
  }

  private Set<String> names() {
    return substituteIngredients.stream()
        .map(SubstituteIngredient::name)
        .map(name -> name.strip().toLowerCase(Locale.ROOT))
        .collect(Collectors.toSet());
  }
}
