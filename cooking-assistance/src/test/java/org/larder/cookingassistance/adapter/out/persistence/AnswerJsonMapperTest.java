package org.larder.cookingassistance.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading a stored answer back: every kind is named, none is guessed (step 7, increment 3). */
class AnswerJsonMapperTest {

  @DisplayName(
      "a stored answer of an unknown type (MENU_PROPOSAL) -> IllegalStateException naming the type, not read as a preparation-step explanation -- B4-S02 US-CA-04 ADR0015 (step 13 m-2)")
  @Test
  void anUnknownStoredAnswerTypeIsRefused() {
    AnswerJson stored =
        new AnswerJson(
            """
            {"answerType":"MENU_PROPOSAL","recipe":"5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e",
             "howToStep":"7a1e5c2b-3d4f-4e6a-8b9c-0d1e2f3a4b5c","description":"Make it a stew",
             "images":[]}""");

    assertThatThrownBy(() -> AnswerJsonMapper.toAnswer(stored))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MENU_PROPOSAL");
  }

  @DisplayName(
      "a stored answer with no answerType -> IllegalStateException \"Unknown stored answer type\", as any unknown type -- B4-S02 US-CA-04 ADR0015 (step 13 R-2)")
  @Test
  void aStoredAnswerWithoutAnswerTypeIsRefused() {
    AnswerJson stored =
        new AnswerJson(
            """
            {"recipe":"5b6c7d8e-9f0a-4b1c-8d2e-3f4a5b6c7d8e","explanation":"Cut it off",
             "images":[]}""");

    assertThatThrownBy(() -> AnswerJsonMapper.toAnswer(stored))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown stored answer type");
  }
}
