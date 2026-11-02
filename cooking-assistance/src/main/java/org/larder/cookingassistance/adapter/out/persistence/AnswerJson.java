package org.larder.cookingassistance.adapter.out.persistence;

/** A Help's answer as JSON text, stored in a jsonb column (ADR0009 addition, step 7). */
record AnswerJson(String value) {}
