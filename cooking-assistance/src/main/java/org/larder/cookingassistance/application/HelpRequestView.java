package org.larder.cookingassistance.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.larder.cookingassistance.domain.helprequest.AskedProvider;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpProviderType;
import org.larder.cookingassistance.domain.helprequest.HelpRequest;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestStatus;
import org.larder.cookingassistance.domain.helprequest.HelpType;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.Note;
import org.larder.cookingassistance.domain.helprequest.RecipeId;

/** A help request as the read side shows it: plain values and one derived read rule. */
public record HelpRequestView(
    HelpRequestId id,
    CookId requester,
    String title,
    HelpType type,
    String description,
    RecipeId recipe,
    HowToStepId howToStep,
    List<AskedProvider> askedProviders,
    HelpRequestStatus status,
    List<Note> notes,
    List<PreviousNote> previousNotes,
    Optional<HelpId> acceptedHelp) {

  /**
   * The providers the cook chose: the request asks exactly the preferred providers, so they are the
   * asked providers' types until re-sending (increment 1 stores them separately).
   */
  public Set<HelpProviderType> preferredProviders() {
    return askedProviders.stream().map(AskedProvider::providerType).collect(Collectors.toSet());
  }

  /** INV-09: the providers the domain suggests once every asked provider timed out. */
  public List<HelpProviderType> suggestedProviders() {
    return HelpRequest.suggestionsAfterTimeOut(askedProviders);
  }
}
