package org.larder.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** What every story checks on a Help Request, whichever kind of help Lena asks for. */
final class HelpRequestAssertions {

  private HelpRequestAssertions() {}

  /** An asked provider's deadline, within the seconds the clock ticked while the steps ran. */
  static void assertDeadline(JsonNode deadline, Instant expected, String whose) {
    assertThat(OffsetDateTime.parse(deadline.asText()).toInstant())
        .as("%s's deadline", whose)
        .isCloseTo(expected, within(5, ChronoUnit.SECONDS));
  }

  /**
   * A later request shows Lena's one earlier note as a rejection, linked to the request she noted
   * it on (INV-24).
   */
  static void assertShowsThePreviousNote(JsonNode request, String note, String earlierRequest) {
    JsonNode previous = request.get("previousNotes");
    assertThat(previous).as("previousNotes of the new request").isNotNull().hasSize(1);
    assertThat(previous.get(0).get("kind").asText()).isEqualTo("REJECTION");
    assertThat(previous.get(0).get("text").asText()).isEqualTo(note);
    assertThat(previous.get(0).get("helpRequestLink").asText())
        .endsWith("/help-requests/" + earlierRequest);
  }
}
