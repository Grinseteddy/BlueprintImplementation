package org.larder.cookingassistance.domain.helprequest;

/** Kind of help a cook asks for; increment 1 builds requests for preparation steps only. */
public enum HelpType {
  INGREDIENT_SUBSTITUTE,
  PREPARATION_STEP_EXPLANATION,
  STEPS_TO_MITIGATE_CATASTROPHE,
  MENU_PROPOSAL
}
