package org.larder.cookingassistance.domain.helprequest;

/** What a Help says; its kind is the kind of help that was asked for. */
public sealed interface HelpAnswer permits PreparationStepExplanation, Substitutes {}
