package org.larder.cookingassistance.domain.helprequest;

import java.util.UUID;

/** Reference to the one ingredient of a recipe a substitute is asked for. */
public record IngredientId(UUID value) {}
