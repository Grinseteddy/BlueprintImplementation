package org.larder.cookingassistance.domain.helprequest;

import java.util.Optional;

/** A Help answering a Help Request; an entity of the Help Request aggregate (SADR0008). */
public final class Help {

  private final HelpId id;
  private final HelpProvider provider;
  private final String answerTitle;
  private final PreparationStepExplanation answer;
  private final boolean accepted;
  private final boolean rejected;

  Help(HelpId id, HelpProvider provider, String answerTitle, PreparationStepExplanation answer) {
    this(id, provider, answerTitle, answer, false, false);
  }

  private Help(
      HelpId id,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      boolean accepted,
      boolean rejected) {
    this.id = id;
    this.provider = provider;
    this.answerTitle = answerTitle;
    this.answer = answer;
    this.accepted = accepted;
    this.rejected = rejected;
  }

  /** Rebuilds a stored Help: no guards. */
  public static Help reconstitute(
      HelpId id,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      boolean accepted) {
    return new Help(id, provider, answerTitle, answer, accepted, false);
  }

  /** This Help, accepted by the requester. */
  Help accepted() {
    return new Help(id, provider, answerTitle, answer, true, rejected);
  }

  /** This Help, rejected by the requester (INV-39: never accepted later). */
  Help rejected() {
    return new Help(id, provider, answerTitle, answer, accepted, true);
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

  public boolean isRejected() {
    return rejected;
  }
}
