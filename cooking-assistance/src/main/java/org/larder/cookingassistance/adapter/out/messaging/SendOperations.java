package org.larder.cookingassistance.adapter.out.messaging;

import java.util.Set;

/** The send operations of cooking-assistance.asyncapi.yaml that are built (coverage gate). */
final class SendOperations {

  static final Set<String> BUILT =
      Set.of(
          // op: publishHelpRequestCreated · evt: Help requested
          "publishHelpRequestCreated",
          // op: publishHelpProvided · evt: Help provided
          "publishHelpProvided",
          // op: publishHelpAccepted · evt: Help accepted
          "publishHelpAccepted",
          // op: publishHelpRejected · evt: Help rejected
          "publishHelpRejected",
          // op: publishHelpRequestCancelled · evt: Help request cancelled
          "publishHelpRequestCancelled",
          // op: publishHelpRequestTimedOut · evt: Help request timed out
          "publishHelpRequestTimedOut");

  private SendOperations() {}
}
