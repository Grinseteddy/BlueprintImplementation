package org.larder.cookingassistance.domain.helprequest;

import java.util.Optional;

/** Who gives a Help: a kind of provider and, for a person, who they are. */
public record HelpProvider(HelpProviderType type, Optional<CookId> person) {

  public static HelpProvider grandmaAvatar() {
    return new HelpProvider(HelpProviderType.GRANDMA_AVATAR, Optional.empty());
  }

  public static HelpProvider community(CookId member) {
    return new HelpProvider(HelpProviderType.COMMUNITY, Optional.of(member));
  }
}
