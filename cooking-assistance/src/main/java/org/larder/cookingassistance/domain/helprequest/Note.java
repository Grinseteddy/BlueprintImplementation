package org.larder.cookingassistance.domain.helprequest;

import java.time.Instant;
import java.util.Optional;

/**
 * A note the requester wrote when rejecting a Help (naming that Help) or cancelling the request
 * (INV-07).
 */
public record Note(NoteKind kind, String text, Instant writtenAt, Optional<HelpId> help) {

  public Note {
    // INV-06: a rejection note names the Help that didn't work
    if (kind == NoteKind.REJECTION && help.isEmpty()) {
      throw new IllegalArgumentException("a rejection note names the rejected Help");
    }
    // INV-08: a cancellation note is about the request, not about one Help
    if (kind == NoteKind.CANCELLATION && help.isPresent()) {
      throw new IllegalArgumentException("a cancellation note names no Help");
    }
  }

  // INV-06, D-3 (step 13): a blank text says nothing, so it is no note at all
  static boolean saysSomething(String text) {
    return text != null && !text.isBlank();
  }

  static Note rejection(String text, Instant writtenAt, HelpId rejectedHelp) {
    return new Note(NoteKind.REJECTION, text, writtenAt, Optional.of(rejectedHelp));
  }

  static Note cancellation(String text, Instant writtenAt) {
    return new Note(NoteKind.CANCELLATION, text, writtenAt, Optional.empty());
  }
}
