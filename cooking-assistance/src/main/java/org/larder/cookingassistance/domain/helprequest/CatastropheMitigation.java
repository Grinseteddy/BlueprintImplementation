package org.larder.cookingassistance.domain.helprequest;

import java.net.URI;
import java.util.List;

/** Answer of type Steps to Mitigate Catastrophe; the recipe is absent when none was named. */
public record CatastropheMitigation(RecipeId recipe, String explanation, List<URI> images)
    implements HelpAnswer {

  /** Whether this mitigation names no recipe or the request's own (INV-37). */
  boolean isAbout(RecipeId requestedRecipe) {
    return recipe == null || recipe.equals(requestedRecipe);
  }
}
