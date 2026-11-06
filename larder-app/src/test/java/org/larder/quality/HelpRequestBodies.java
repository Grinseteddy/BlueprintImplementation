package org.larder.quality;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.larder.cookingassistance.fixtures.HelpRequests;

/** The help requests the quality tests send, as JSON request bodies. */
final class HelpRequestBodies {

  private HelpRequestBodies() {}

  /** Lena's hollandaise splits at step 4; she asks the given providers to explain the step. */
  static String sauceSplitsAtStep4(String... preferredProviders) {
    return """
        {"title":"Sauce splits","type":"PREPARATION_STEP_EXPLANATION","description":"Step 4",
         "recipe":"%s","howToStep":"%s","preferredProvider":[%s]}"""
        .formatted(
            HelpRequests.HOLLANDAISE.value(),
            HelpRequests.STEP_4.value(),
            Arrays.stream(preferredProviders)
                .map(provider -> '"' + provider + '"')
                .collect(Collectors.joining(",")));
  }
}
