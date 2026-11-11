package org.larder.cookingassistance.domain.helprequest;

import java.net.URI;
import java.util.List;

/** Answer of type Preparation Step Explanation. */
public record PreparationStepExplanation(
    RecipeId recipe, HowToStepId howToStep, String description, List<URI> images)
    implements HelpAnswer {

  /** Whether this explanation is about the given step of the given recipe (INV-37). */
  boolean isAbout(RecipeId requestedRecipe, HowToStepId requestedStep) {
    return recipe.equals(requestedRecipe) && howToStep.equals(requestedStep);
  }
}
