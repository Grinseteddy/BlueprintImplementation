package org.larder.cookingassistance.adapter.out.messaging;

import java.util.Set;

/** The send operations of cooking-assistance.asyncapi.yaml: built, or planned (coverage gate). */
final class SendOperations {

  // op: publishHelpRequestCreated · evt: Help requested; op: publishHelpProvided · evt: Help provided
  static final Set<String> BUILT = Set.of("publishHelpRequestCreated", "publishHelpProvided");

  // board B3, slices of increment 1 (accept, reject, cancel, time out)
  static final Set<String> PLANNED_FOR_INCREMENT_1 =
      Set.of(
          "publishHelpAccepted",
          "publishHelpRejected",
          "publishHelpRequestCancelled",
          "publishHelpRequestTimedOut");

  private SendOperations() {}
}
