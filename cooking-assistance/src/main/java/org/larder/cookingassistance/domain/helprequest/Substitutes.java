package org.larder.cookingassistance.domain.helprequest;

import java.util.List;

/** Answer of type Ingredient Substitute. */
public record Substitutes(RecipeId recipe, List<Substitute> substitute) implements HelpAnswer {

  /** Whether at least one substitute is proposed, each naming its ingredients (INV-12). */
  boolean proposesSomething() {
    return !substitute.isEmpty() && substitute.stream().allMatch(Substitute::namesItsIngredients);
  }

  /** Whether every substitute is for the given ingredient of the given recipe (INV-37). */
  boolean isAbout(RecipeId requestedRecipe, IngredientId requestedIngredient) {
    return recipe.equals(requestedRecipe)
        && substitute.stream().allMatch(one -> one.ingredient().equals(requestedIngredient));
  }

  /** Whether any substitute is the same as one of the rejected ones (INV-15). */
  boolean repeatsAnyOf(List<Substitute> rejected) {
    return substitute.stream().anyMatch(one -> rejected.stream().anyMatch(one::isSameAs));
  }
}
