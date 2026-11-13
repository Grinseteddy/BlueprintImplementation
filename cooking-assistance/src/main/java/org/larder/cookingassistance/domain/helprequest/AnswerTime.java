package org.larder.cookingassistance.domain.helprequest;

import java.time.Duration;

/** How long an asked provider has to answer, by the kind of help and the provider. */
final class AnswerTime {

  // INV-09: a provider asked about a preparation step answers within 12 h (US-CA-03 R8);
  // INV-06: asked again after a rejection, it has the same 12 h again (US-CA-03 R5)
  private static final Duration PREPARATION_STEP = Duration.ofHours(12);
  // INV-17: an ingredient request has 24 h
  private static final Duration INGREDIENT = Duration.ofHours(24);
  // INV-26: a catastrophe request has 15 min for the Community, 5 min for the Grandma Avatar
  private static final Duration CATASTROPHE_COMMUNITY = Duration.ofMinutes(15);
  private static final Duration CATASTROPHE_GRANDMA = Duration.ofMinutes(5);

  private AnswerTime() {}

  // A provider's own answer time for the kind of help: INV-09: 12 h for a preparation step;
  // INV-17: 24 h for an ingredient; INV-26: for a catastrophe 15 min for the Community, 5 min for
  // the Grandma Avatar
  static Duration of(HelpType type, HelpProviderType provider) {
    return switch (type) {
      case INGREDIENT_SUBSTITUTE -> INGREDIENT;
      case STEPS_TO_MITIGATE_CATASTROPHE ->
          provider == HelpProviderType.COMMUNITY ? CATASTROPHE_COMMUNITY : CATASTROPHE_GRANDMA;
      default -> PREPARATION_STEP;
    };
  }
}
