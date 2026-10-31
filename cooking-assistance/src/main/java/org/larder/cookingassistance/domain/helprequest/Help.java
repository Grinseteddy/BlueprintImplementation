package org.larder.cookingassistance.domain.helprequest;

import java.util.Optional;

/** A Help answering a Help Request; an entity of the Help Request aggregate (SADR0008). */
public final class Help {

  private final HelpId id;
  private final HelpProvider provider;
  private final String answerTitle;
  private final PreparationStepExplanation answer;

  Help(HelpId id, HelpProvider provider, String answerTitle, PreparationStepExplanation answer) {
    this.id = id;
    this.provider = provider;
    this.answerTitle = answerTitle;
    this.answer = answer;
  }

  public HelpId id() {
    return id;
  }

  public HelpProviderType providerType() {
    return provider.type();
  }

  public Optional<CookId> provider() {
    return provider.person();
  }

  public String answerTitle() {
    return answerTitle;
  }

  public PreparationStepExplanation answer() {
    return answer;
  }

  public boolean isAccepted() {
    return false;
  }
}
