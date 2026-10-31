package org.larder.cookingassistance.domain.helprequest;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Help Request aggregate. */
public final class HelpRequest {

  // INV-09: a provider asked about a preparation step answers within 12 h (US-CA-03 R8)
  private static final Duration PREPARATION_STEP_ANSWER_TIME = Duration.ofHours(12);

  // INV-38: a request or an answer carries at most 10 pictures (glossary Picture 0..10)
  private static final int MAX_PICTURES = 10;

  private final HelpRequestId id;
  private final CookId requester;
  private final String title;
  private final String description;
  private final HelpType type;
  private final RecipeId recipe;
  private final HowToStepId howToStep;
  private List<AskedProvider> askedProviders;
  private HelpRequestStatus status;
  private final long version;
  private final List<Help> helps = new ArrayList<>();
  private final List<HelpRequestEvent> pendingEvents = new ArrayList<>();

  private HelpRequest(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      List<AskedProvider> askedProviders,
      HelpRequestStatus status,
      long version) {
    this.id = id;
    this.requester = requester;
    this.title = title;
    this.description = description;
    this.type = type;
    this.recipe = recipe;
    this.howToStep = howToStep;
    this.askedProviders = askedProviders;
    this.status = status;
    this.version = version;
  }

  // slice: B3-S01, story: US-CA-03, cmd: Request help Preparation step
  public static HelpRequest requestHelpWithPreparationStep(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      RecipeId recipe,
      HowToStepId howToStep,
      Set<HelpProviderType> preferredProviders,
      Clock clock) {
    ensureProvidersMayBeAsked(preferredProviders);
    ensureOneStepOfOneRecipe(recipe, howToStep);
    Instant requestedAt = clock.instant();
    List<AskedProvider> asked = askPreferredProviders(preferredProviders, requestedAt);
    HelpRequest request =
        new HelpRequest(
            id,
            requester,
            title,
            description,
            HelpType.PREPARATION_STEP_EXPLANATION,
            recipe,
            howToStep,
            asked,
            HelpRequestStatus.OPEN,
            0L);
    // evt: Help requested
    request.pendingEvents.add(
        new HelpRequested(
            request.id,
            request.requester,
            request.title,
            request.type,
            request.description,
            request.recipe,
            request.howToStep,
            request.askedProviders,
            request.status,
            requestedAt));
    return request;
  }

  /** Rebuilds a stored Help Request: no guards, no events. */
  public static HelpRequest reconstitute(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      List<AskedProvider> askedProviders,
      HelpRequestStatus status,
      List<Help> helps,
      long version) {
    HelpRequest request =
        new HelpRequest(
            id, requester, title, description, type, recipe, howToStep, askedProviders, status,
            version);
    request.helps.addAll(helps);
    return request;
  }

  // INV-01: help with a preparation step goes to 1..2 providers - the Community and/or
  // Grandma Avatar, never a Chef (US-CA-03 R1)
  private static void ensureProvidersMayBeAsked(Set<HelpProviderType> preferredProviders) {
    if (preferredProviders.isEmpty()) {
      throw new NoHelpProviderChosen();
    }
    if (preferredProviders.contains(HelpProviderType.CHEF)) {
      throw new ChefNotAllowed();
    }
  }

  // INV-02: a preparation-step request names one numbered step of one recipe (US-CA-03 R2)
  private static void ensureOneStepOfOneRecipe(RecipeId recipe, HowToStepId howToStep) {
    if (recipe == null) {
      throw new RecipeRequired();
    }
    if (howToStep == null) {
      throw new PreparationStepRequired();
    }
  }

  // INV-09: each asked provider's deadline is the request time + 12 h (US-CA-03 R8)
  private static List<AskedProvider> askPreferredProviders(
      Set<HelpProviderType> preferredProviders, Instant requestedAt) {
    Instant deadline = requestedAt.plus(PREPARATION_STEP_ANSWER_TIME);
    return preferredProviders.stream().map(type -> new AskedProvider(type, deadline)).toList();
  }

  // slice: B3-S02, story: US-CA-03, cmd: Provide Help
  public void provideHelp(
      HelpId helpId,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      Clock clock) {
    ensureOpenForAnswers();
    ensureProviderWasAsked(provider);
    ensureNotOwnRequest(provider);
    ensureAtMostTenPictures(answer.images());
    ensureAnswerMatchesRequest(answer);
    helps.add(new Help(helpId, provider, answerTitle, answer));
    status = HelpRequestStatus.ANSWERED;
    markSideAnswered(provider);
    // evt: Help provided
    pendingEvents.add(
        new HelpProvided(
            helpId,
            id,
            requester,
            answerTitle,
            provider.type(),
            provider.person(),
            answer,
            status,
            clock.instant()));
  }

  /** Each asked provider has its own state: only the answering provider's side is ANSWERED. */
  private void markSideAnswered(HelpProvider provider) {
    askedProviders =
        askedProviders.stream()
            .map(asked -> asked.providerType() == provider.type() ? asked.answered() : asked)
            .toList();
  }

  public List<Help> helps() {
    return List.copyOf(helps);
  }

  // Lifecycle (design brief §3): only OPEN and ANSWERED requests take answers
  private void ensureOpenForAnswers() {
    if (status.isFinal()) {
      throw new InvalidHelpRequestState(status);
    }
  }

  // INV-01: the request goes to the providers the cook chose; only they answer
  private void ensureProviderWasAsked(HelpProvider provider) {
    if (!askedProviderTypes().contains(provider.type())) {
      throw new ProviderNotAsked();
    }
  }

  // INV-36: a cook never answers their own request (decision D3)
  private void ensureNotOwnRequest(HelpProvider provider) {
    if (provider.person().equals(Optional.of(requester))) {
      throw new OwnRequest();
    }
  }

  // INV-37: an answer is about the request's recipe and step (US-CA-03 S3.E2)
  private void ensureAnswerMatchesRequest(PreparationStepExplanation answer) {
    if (!answer.isAbout(recipe, howToStep)) {
      throw new AnswerDoesNotMatchRequest();
    }
  }

  // INV-38: a request or an answer carries at most 10 pictures
  private static void ensureAtMostTenPictures(List<URI> pictures) {
    if (pictures.size() > MAX_PICTURES) {
      throw new TooManyPictures();
    }
  }

  public HelpRequestId id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  /** Version for optimistic locking (ADR0010); set by persistence, never incremented here. */
  public long version() {
    return version;
  }

  public HelpRequestStatus status() {
    return status;
  }

  public CookId requester() {
    return requester;
  }

  public HelpType type() {
    return type;
  }

  public RecipeId recipe() {
    return recipe;
  }

  public HowToStepId howToStep() {
    return howToStep;
  }

  public List<AskedProvider> askedProviders() {
    return askedProviders;
  }

  public List<HelpProviderType> askedProviderTypes() {
    return askedProviders.stream().map(AskedProvider::providerType).toList();
  }

  /** Hands over the recorded events once; the application service publishes them. */
  public List<HelpRequestEvent> pullEvents() {
    List<HelpRequestEvent> pulled = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return pulled;
  }
}
