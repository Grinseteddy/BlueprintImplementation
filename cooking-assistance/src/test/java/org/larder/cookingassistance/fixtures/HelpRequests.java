package org.larder.cookingassistance.fixtures;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProvider;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.PreparationStepExplanation;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** Help Requests built through the domain's public commands, shared by contract and integration tests. */
public final class HelpRequests {

  public static final HelpRequestId HELP_REQUEST_ID =
      new HelpRequestId(UUID.fromString("23a8eeed-35f6-460b-892e-7bb458a8fded"));
  public static final CookId LENA = new CookId(UUID.fromString("f64e07f0-f9e7-4b7a-8695-e7a8b3ef5074"));
  public static final RecipeId HOLLANDAISE =
      new RecipeId(UUID.fromString("7cf09822-77a1-46bb-812f-b7852bca0913"));
  public static final HowToStepId STEP_4 =
      new HowToStepId(UUID.fromString("65610dee-fb83-4341-a730-26a4a99a621a"));
  /** Friday 18:00 CET. */
  public static final Clock FRIDAY_18_00 =
      Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneOffset.UTC);

  public static final HelpId HELP_ID = new HelpId(UUID.fromString("a9caf90d-00b4-4a66-8184-7d02152e8d6a"));
  /** Friday 18:10 CET. */
  public static final Clock FRIDAY_18_10 =
      Clock.fixed(Instant.parse("2026-11-06T17:10:00Z"), ZoneOffset.UTC);
  public static final PreparationStepExplanation BUTTER_WITH_THREE_PHOTOS =
      new PreparationStepExplanation(
          HOLLANDAISE,
          STEP_4,
          "Add the butter in a thin stream",
          List.of(
              URI.create("https://larder.org/media/images/photo-1"),
              URI.create("https://larder.org/media/images/photo-2"),
              URI.create("https://larder.org/media/images/photo-3")));

  public static final HelpId OTHER_HELP_ID =
      new HelpId(UUID.fromString("1e7c2a90-3a5b-4b1d-8f0e-7c2d9b1a4e22"));
  /** Jonas, a Community member. */
  public static final CookId JONAS = new CookId(UUID.fromString("39a7aed5-2e50-48c8-8aa6-f3afa9f03f74"));
  /** Mia, a Community member. */
  public static final CookId MIA = new CookId(UUID.fromString("4c5d6e7f-8a9b-4c0d-9e1f-2a3b4c5d6e70"));

  private HelpRequests() {}

  /** A Community member explains step 4 on Friday 18:10. */
  public static void communityMemberExplainsStep4(HelpRequest request, HelpId helpId, CookId member) {
    request.provideHelp(
        helpId,
        HelpProvider.community(member),
        "Warm the bowl",
        BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_18_10);
  }

  /** Grandma Avatar explains step 4 with three photos on Friday 18:10. */
  public static void grandmaExplainsStep4(HelpRequest request) {
    request.provideHelp(
        HELP_ID,
        HelpProvider.grandmaAvatar(),
        "Butter in a thin stream",
        BUTTER_WITH_THREE_PHOTOS,
        FRIDAY_18_10);
  }

  /** Lena asks the given providers about step 4 of her Hollandaise on Friday 18:00. */
  public static HelpRequest lenaAsksAboutStep4(HelpProviderType... providers) {
    return HelpRequest.requestHelpWithPreparationStep(
        HELP_REQUEST_ID,
        LENA,
        "How do I keep the sauce from splitting?",
        "Step 4 of my Hollandaise keeps splitting",
        HOLLANDAISE,
        STEP_4,
        Set.of(providers),
        FRIDAY_18_00);
  }
}
