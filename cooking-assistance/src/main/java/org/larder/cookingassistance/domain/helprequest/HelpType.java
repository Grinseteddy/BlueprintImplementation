package org.larder.cookingassistance.domain.helprequest;

/** Kind of help a cook asks for; requests are built for preparation steps and ingredients. */
public enum HelpType {
  INGREDIENT_SUBSTITUTE,
  PREPARATION_STEP_EXPLANATION,
  STEPS_TO_MITIGATE_CATASTROPHE,
  MENU_PROPOSAL
}
