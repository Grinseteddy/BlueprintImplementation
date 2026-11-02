package org.larder.cookingassistance.domain.helprequest;

// INV-37: an answer is about the same type, recipe and step / ingredient as its request
/** The answer is about something else than its Help Request. */
public final class AnswerDoesNotMatchRequest extends DomainRejection {

  public AnswerDoesNotMatchRequest() {
    super(
        "INV-37",
        "ANSWER_DOES_NOT_MATCH_REQUEST",
        "The answer must be about the request's recipe and step");
  }
}
