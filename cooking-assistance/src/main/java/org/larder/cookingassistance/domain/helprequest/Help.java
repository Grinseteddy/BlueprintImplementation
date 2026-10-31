package org.larder.cookingassistance.domain.helprequest;

import java.util.Optional;

/** A Help answering a Help Request; an entity of the Help Request aggregate (SADR0008). */
public final class Help {

  private final HelpId id;
  private final HelpProvider provider;
  private final String answerTitle;
  private final PreparationStepExplanation answer;
  private final boolean accepted;

  Help(HelpId id, HelpProvider provider, String answerTitle, PreparationStepExplanation answer) {
    this(id, provider, answerTitle, answer, false);
  }

  private Help(
      HelpId id,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      boolean accepted) {
    this.id = id;
    this.provider = provider;
    this.answerTitle = answerTitle;
    this.answer = answer;
    this.accepted = accepted;
  }

  /** Rebuilds a stored Help: no guards. */
  public static Help reconstitute(
      HelpId id,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      boolean accepted) {
    return new Help(id, provider, answerTitle, answer, accepted);
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
    return accepted;
  }
}
