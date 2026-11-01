package org.larder.cookingassistance.adapter.in.rest;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Operations of tag Helps (provideHelp) through the real web layer, real resource-server security and the
 * step 8 use cases with contract-checked fakes (no database).
 */
class HelpsControllerTest {

  @Disabled("step 9 test list")
  @DisplayName("Jonas (community, help:write) answers Lena's PSE request as COMMUNITY -> 201 {helpLink: .../help-requests/<id>/helps/<helpId>} matching HelpLink; the help is stored as Jonas's -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void jonasProvidesHelp() {}

  @Disabled("step 9 test list")
  @DisplayName("answerTitle missing -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void missingAnswerTitleIsRejected() {}

  @Disabled("step 9 test list")
  @DisplayName("helpProviderType \"ROBOT\" -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void invalidProviderTypeIsRejected() {}

  @Disabled("step 9 test list")
  @DisplayName("PSE answer without description -> 400 VALIDATION_FAILED, nothing saved -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void explanationWithoutDescriptionIsRejected() {}

  @Disabled("step 9 test list")
  @DisplayName("11 images -> 400 VALIDATION_FAILED, nothing saved (schema maxItems) -- B3-S02 US-CA-03 INV-38 op provideHelp")
  @Test
  void elevenImagesAreRejected() {}

  @Disabled("step 9 test list")
  @DisplayName("unknown help request -> 404 NOT_FOUND -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void unknownHelpRequestIsNotFound() {}

  @Disabled("step 9 test list")
  @DisplayName("Lena answers her own request -> 403 OWN_REQUEST with rule INV-36 -- B3-S02 US-CA-03 INV-36 op provideHelp")
  @Test
  void ownRequestIsForbidden() {}

  @Disabled("step 9 test list")
  @DisplayName("request sent only to Grandma Avatar -> 403 PROVIDER_NOT_ASKED with rule INV-01 -- B3-S02 US-CA-03 INV-01 op provideHelp")
  @Test
  void providerNotAskedIsForbidden() {}

  @Disabled("step 9 test list")
  @DisplayName("CANCELLED request -> 409 INVALID_STATE with rule LIFECYCLE -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void cancelledRequestIsAConflict() {}

  @Disabled("step 9 test list")
  @DisplayName("answer about step 6 -> 422 ANSWER_DOES_NOT_MATCH_REQUEST with rule INV-37 -- B3-S02 US-CA-03 INV-37 op provideHelp")
  @Test
  void answerAboutAnotherStepIsARuleViolation() {}

  @Disabled("step 9 test list")
  @DisplayName("conflict on both tries -> 409 CONCURRENT_UPDATE -- B3-S02 US-CA-03 ADR0010 op provideHelp")
  @Test
  void conflictOnBothTriesIsAConcurrentUpdate() {}

  @Disabled("step 9 test list")
  @DisplayName("helpProviderType GRANDMA_AVATAR, then CHEF -> 403 NOT_AUTHORIZED each -- B3-S02 US-CA-03 ADR0013 op provideHelp")
  @Test
  void grandmaAvatarOrChefThroughTheApiIsRefused() {}

  @Disabled("step 9 test list")
  @DisplayName("actor without role community -> 403 NOT_AUTHORIZED, nothing saved -- B3-S02 US-CA-03 ADR0013 op provideHelp")
  @Test
  void actorWithoutCommunityRoleIsRefused() {}

  @Disabled("step 9 test list")
  @DisplayName("valid answer of kind Substitutes -> 501 NOT_IMPLEMENTED, nothing saved (increment 2) -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void substitutesAnswerIsNotImplementedYet() {}

  @Disabled("step 9 test list")
  @DisplayName("no token -> 401 UNAUTHENTICATED; scope help:read only -> 403 INSUFFICIENT_SCOPE -- B3-S02 US-CA-03 ADR0012 ADR0013 op provideHelp")
  @Test
  void noTokenOrReadScopeCannotProvideHelp() {}

  @Disabled("step 9 test list")
  @DisplayName("unexpected error -> 500 INTERNAL_ERROR without the internal message -- B3-S02 US-CA-03 op provideHelp")
  @Test
  void unexpectedErrorHidesItsMessage() {}

  @Disabled("step 9 test list")
  @DisplayName("each of the 7 unbuilt operations of both interfaces -> 501 NOT_IMPLEMENTED (parameterized) -- B3-S02 US-CA-03 ADR0014")
  @Test
  void unbuiltOperationsAreNotImplementedYet() {}
}
