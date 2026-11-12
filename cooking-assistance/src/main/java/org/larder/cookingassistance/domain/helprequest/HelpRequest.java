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

  // INV-09: a provider asked about a preparation step answers within 12 h (US-CA-03 R8);
  // INV-06: asked again after a rejection, it has the same 12 h again (US-CA-03 R5)
  private static final Duration PREPARATION_STEP_ANSWER_TIME = Duration.ofHours(12);
  // INV-17: an ingredient request has 24 h
  private static final Duration INGREDIENT_ANSWER_TIME = Duration.ofHours(24);

  // INV-01, INV-10: help with a preparation step or an ingredient comes from the Community or
  // Grandma Avatar, never a Chef; INV-09, INV-17: a time-out suggests only these
  private static final List<HelpProviderType> PROVIDERS_TO_SUGGEST =
      List.of(HelpProviderType.COMMUNITY, HelpProviderType.GRANDMA_AVATAR);

  // INV-38: a request or an answer carries at most 10 pictures (glossary Picture 0..10)
  private static final int MAX_PICTURES = 10;

  private final HelpRequestId id;
  private final CookId requester;
  private final String title;
  private final String description;
  private final HelpType type;
  private final RecipeId recipe;
  private final HowToStepId howToStep;
  private final IngredientId ingredient;
  private final String ingredientName;
  private final String substituteRequirements;
  private List<AskedProvider> askedProviders;
  private HelpRequestStatus status;
  private final long version;
  private final List<Help> helps = new ArrayList<>();
  private final List<Note> notes = new ArrayList<>();
  private final List<HelpRequestEvent> pendingEvents = new ArrayList<>();

  private HelpRequest(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      IngredientId ingredient,
      String ingredientName,
      String substituteRequirements,
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
    this.ingredient = ingredient;
    this.ingredientName = ingredientName;
    this.substituteRequirements = substituteRequirements;
    this.askedProviders = askedProviders;
    this.status = status;
    this.version = version;
  }

  // slice: B2-S01, story: US-CA-02, cmd: Request help ingredients
  public static HelpRequest requestHelpWithIngredient(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      RecipeId recipe,
      IngredientId ingredient,
      String ingredientName,
      String substituteRequirements,
      Set<HelpProviderType> preferredProviders,
      Clock clock) {
    ensureProvidersMayBeAsked(preferredProviders);
    ensureOneIngredientOfOneRecipe(recipe, ingredient, ingredientName, substituteRequirements);
    return opened(
        id,
        requester,
        title,
        description,
        HelpType.INGREDIENT_SUBSTITUTE,
        recipe,
        null,
        ingredient,
        ingredientName,
        substituteRequirements,
        preferredProviders,
        clock);
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
    return opened(
        id,
        requester,
        title,
        description,
        HelpType.PREPARATION_STEP_EXPLANATION,
        recipe,
        howToStep,
        null,
        null,
        null,
        preferredProviders,
        clock);
  }

  /** A new request of any kind: open, version 0, providers asked, Help requested raised. */
  private static HelpRequest opened(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      IngredientId ingredient,
      String ingredientName,
      String substituteRequirements,
      Set<HelpProviderType> preferredProviders,
      Clock clock) {
    Instant requestedAt = clock.instant();
    List<AskedProvider> asked =
        askPreferredProviders(preferredProviders, requestedAt, answerTimeOf(type));
    HelpRequest request =
        new HelpRequest(
            id,
            requester,
            title,
            description,
            type,
            recipe,
            howToStep,
            ingredient,
            ingredientName,
            substituteRequirements,
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
            request.ingredient,
            request.ingredientName,
            request.substituteRequirements,
            request.askedProviders,
            request.status,
            requestedAt));
    return request;
  }

  /** Rebuilds a stored Help Request that names no ingredient: no guards, no events. */
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
      List<Note> notes,
      long version) {
    return reconstitute(
        id,
        requester,
        title,
        description,
        type,
        recipe,
        howToStep,
        null,
        null,
        null,
        askedProviders,
        status,
        helps,
        notes,
        version);
  }

  /** Rebuilds a stored Help Request of any kind: no guards, no events. */
  public static HelpRequest reconstitute(
      HelpRequestId id,
      CookId requester,
      String title,
      String description,
      HelpType type,
      RecipeId recipe,
      HowToStepId howToStep,
      IngredientId ingredient,
      String ingredientName,
      String substituteRequirements,
      List<AskedProvider> askedProviders,
      HelpRequestStatus status,
      List<Help> helps,
      List<Note> notes,
      long version) {
    HelpRequest request =
        new HelpRequest(
            id,
            requester,
            title,
            description,
            type,
            recipe,
            howToStep,
            ingredient,
            ingredientName,
            substituteRequirements,
            askedProviders,
            status,
            version);
    request.helps.addAll(helps);
    request.notes.addAll(notes);
    return request;
  }

  // INV-01, INV-10: help goes to 1..2 providers - the Community and/or Grandma Avatar,
  // never a Chef (US-CA-03 R1, US-CA-02 R1)
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

  // INV-11: an ingredient request names one ingredient of one recipe, by id and by name
  // (US-CA-02 R2, step 3 r15)
  private static void ensureOneIngredientOfOneRecipe(
      RecipeId recipe,
      IngredientId ingredient,
      String ingredientName,
      String substituteRequirements) {
    if (recipe == null) {
      throw new RecipeRequired();
    }
    if (ingredient == null) {
      throw new IngredientRequired();
    }
    if (ingredientName == null || ingredientName.isBlank()) {
      throw new IngredientNameRequired();
    }
    if (substituteRequirements == null || substituteRequirements.isBlank()) {
      throw new SubstituteRequirementsRequired();
    }
  }

  // INV-09, INV-17: each asked provider's deadline is the request time + the type's answer time
  private static List<AskedProvider> askPreferredProviders(
      Set<HelpProviderType> preferredProviders, Instant requestedAt, Duration answerTime) {
    Instant deadline = requestedAt.plus(answerTime);
    return preferredProviders.stream().map(type -> new AskedProvider(type, deadline)).toList();
  }

  // slice: B3-S02, story: US-CA-03, cmd: Provide Help
  public void provideHelp(
      HelpId helpId,
      HelpProvider provider,
      String answerTitle,
      PreparationStepExplanation answer,
      Clock clock) {
    Instant answeredAt = clock.instant();
    ensureProviderMayAnswer(provider, answeredAt);
    ensurePicturesMayBeAttached(answer.images());
    ensureAnswerMatchesRequest(answer);
    recordHelp(helpId, provider, answerTitle, answer, answeredAt);
  }

  // slice: B2-S02, story: US-CA-02, cmd: Provide Help
  public void provideHelp(
      HelpId helpId, HelpProvider provider, String answerTitle, Substitutes answer, Clock clock) {
    Instant answeredAt = clock.instant();
    ensureProviderMayAnswer(provider, answeredAt);
    ensureSomethingProposed(answer);
    ensureSubstitutesMatchRequest(answer);
    ensureNotRejectedBefore(answer);
    recordHelp(helpId, provider, answerTitle, answer, answeredAt);
  }

  // slice: B3-S02/B2-S02, story: US-CA-03/US-CA-02, cmd: Provide Help, for any kind of answer
  public void provideHelp(
      HelpId helpId, HelpProvider provider, String answerTitle, HelpAnswer answer, Clock clock) {
    switch (answer) {
      case PreparationStepExplanation explanation ->
          provideHelp(helpId, provider, answerTitle, explanation, clock);
      case Substitutes substitutes ->
          provideHelp(helpId, provider, answerTitle, substitutes, clock);
    }
  }

  /** Who may answer and until when: the same for every kind of help. */
  private void ensureProviderMayAnswer(HelpProvider provider, Instant answeredAt) {
    ensureNotFinal();
    ensureProviderWasAsked(provider);
    ensureAnsweredInTime(provider, answeredAt);
    ensureNotOwnRequest(provider);
  }

  private void recordHelp(
      HelpId helpId,
      HelpProvider provider,
      String answerTitle,
      HelpAnswer answer,
      Instant answeredAt) {
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
            answeredAt));
  }

  // INV-15: a substitute of a rejected Help is not proposed again in this request (S3)
  private void ensureNotRejectedBefore(Substitutes answer) {
    List<Substitute> rejected =
        helps.stream().flatMap(help -> help.rejectedSubstitutes().stream()).toList();
    if (answer.repeatsAnyOf(rejected)) {
      throw new SubstituteRejectedBefore();
    }
  }

  // INV-37: substitutes are about the request's recipe and its one ingredient
  private void ensureSubstitutesMatchRequest(Substitutes answer) {
    if (!answer.isAbout(recipe, ingredient)) {
      throw new AnswerDoesNotMatchRequest();
    }
  }

  // INV-12: an answer proposes at least one substitute, each naming at least one ingredient with
  // its amount
  private static void ensureSomethingProposed(Substitutes answer) {
    if (!answer.proposesSomething()) {
      throw new NoSubstituteProposed();
    }
  }

  /** Each asked provider has its own state: only the answering provider's side is ANSWERED. */
  private void markSideAnswered(HelpProvider provider) {
    askedProviders =
        askedProviders.stream()
            .map(asked -> asked.isFor(provider) ? asked.answered() : asked)
            .toList();
  }

  // slice: B3-S03, story: US-CA-03/US-CA-02, cmd: Cancel help request
  public void cancel(Optional<String> note, Clock clock) {
    ensureNotFinal();
    Instant cancelledAt = clock.instant();
    // D-3 (step 13): a blank note says nothing and is treated as no note
    Optional<String> said = note.filter(Note::saysSomething);
    // INV-07, INV-08, INV-16: the cancellation note is kept and shown to providers and on future
    // requests
    said.ifPresent(text -> notes.add(Note.cancellation(text, cancelledAt)));
    status = HelpRequestStatus.CANCELLED;
    // evt: Help request cancelled
    pendingEvents.add(
        new HelpRequestCancelled(id, requester, said, askedProviders, status, cancelledAt));
  }

  // slice: B3-S04, story: US-CA-03/US-CA-02, cmd: Accept help
  public void accept(HelpId helpId, Clock clock) {
    ensureAnswered();
    Help help = helpGivenToThisRequest(helpId);
    ensureNotRejected(help);
    replaceHelp(help, help.accepted());
    status = HelpRequestStatus.CLOSED;
    // evt: Help accepted
    pendingEvents.add(
        new HelpAccepted(
            id, requester, helpId, help.providerType(), help.provider(), status, clock.instant()));
  }

  // slice: B3-S05, story: US-CA-03/US-CA-02, cmd: Reject help
  public void reject(HelpId helpId, String note, Clock clock) {
    ensureAnswered();
    ensureNoteGiven(note);
    Help help = helpGivenToThisRequest(helpId);
    ensureNotRejected(help);
    Instant rejectedAt = clock.instant();
    // INV-06, INV-07, INV-15: what didn't work is kept and shown to providers and on future
    // requests
    notes.add(Note.rejection(note, rejectedAt, helpId));
    replaceHelp(help, help.rejected());
    askAgain(rejectedAt);
    status = HelpRequestStatus.OPEN;
    // evt: Help rejected
    pendingEvents.add(
        new HelpRejected(id, requester, helpId, note, askedProviders, status, rejectedAt));
  }

  // INV-06, INV-14: sent again to the same providers, each with a new deadline from the rejection
  private void askAgain(Instant rejectedAt) {
    Instant newDeadline = rejectedAt.plus(answerTimeOf(type));
    askedProviders = askedProviders.stream().map(asked -> asked.askedAgain(newDeadline)).toList();
  }

  // INV-09: 12 h for a preparation step; INV-17: 24 h for an ingredient
  private static Duration answerTimeOf(HelpType type) {
    return type == HelpType.INGREDIENT_SUBSTITUTE
        ? INGREDIENT_ANSWER_TIME
        : PREPARATION_STEP_ANSWER_TIME;
  }

  /** A Help is immutable: marking it swaps it for its marked copy in this request. */
  private void replaceHelp(Help help, Help marked) {
    helps.replaceAll(candidate -> candidate == help ? marked : candidate);
  }

  // INV-06: a rejection says what didn't work (step 3 r5)
  private static void ensureNoteGiven(String note) {
    if (!Note.saysSomething(note)) {
      throw new RejectionNoteRequired();
    }
  }

  // INV-39: a rejected answer is never accepted or rejected again (step 3 r5)
  private static void ensureNotRejected(Help help) {
    if (help.isRejected()) {
      throw new HelpAlreadyRejected();
    }
  }

  // INV-05: only a Help given to this request can be accepted or rejected
  private Help helpGivenToThisRequest(HelpId helpId) {
    return helps.stream()
        .filter(help -> help.id().equals(helpId))
        .findFirst()
        .orElseThrow(HelpNotInRequest::new);
  }

  // slice: B3-S06, story: US-CA-03/US-CA-02, cmd: Time out help request (clock)
  public void timeOut(Clock clock) {
    Instant timedOutAt = clock.instant();
    ensureOpen();
    ensureEveryDeadlinePassed(timedOutAt);
    List<HelpProviderType> timedOutProviders = askedProviderTypes();
    askedProviders = askedProviders.stream().map(AskedProvider::timedOut).toList();
    status = HelpRequestStatus.CANCELLED;
    // evt: Help request timed out
    pendingEvents.add(
        new HelpRequestTimedOut(
            id,
            requester,
            timedOutProviders,
            suggestionsAfterTimeOut(askedProviders),
            status,
            timedOutAt));
  }

  /**
   * INV-09, INV-17: the providers suggested to the requester after a time-out (event and read
   * side): those not yet asked, once every asked provider has timed out.
   */
  public static List<HelpProviderType> suggestionsAfterTimeOut(List<AskedProvider> asked) {
    if (asked.stream().anyMatch(provider -> provider.state() != ProviderState.TIMED_OUT)) {
      return List.of();
    }
    List<HelpProviderType> askedTypes = asked.stream().map(AskedProvider::providerType).toList();
    return PROVIDERS_TO_SUGGEST.stream()
        .filter(provider -> !askedTypes.contains(provider))
        .toList();
  }

  // INV-09, INV-17: a request times out only after every asked provider's deadline (step 3 r5)
  private void ensureEveryDeadlinePassed(Instant timedOutAt) {
    if (!askedProviders.stream().allMatch(asked -> asked.deadlinePassedAt(timedOutAt))) {
      throw new DeadlineNotPassed();
    }
  }

  // Lifecycle (design brief §3): only an Answered request can be accepted or rejected
  private void ensureAnswered() {
    if (status != HelpRequestStatus.ANSWERED) {
      throw new InvalidHelpRequestState(status);
    }
  }

  // Lifecycle (D5, step 3 r5): only an Open request times out
  private void ensureOpen() {
    if (status != HelpRequestStatus.OPEN) {
      throw new InvalidHelpRequestState(status);
    }
  }

  // Lifecycle (design brief §3): a Closed or Cancelled request accepts no further command
  private void ensureNotFinal() {
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

  // INV-09, INV-17: an answer counts at or before the provider's deadline, not after it
  // (step 3 r5)
  private void ensureAnsweredInTime(HelpProvider provider, Instant answeredAt) {
    boolean late =
        askedProviders.stream()
            .anyMatch(asked -> asked.isFor(provider) && asked.deadlinePassedAt(answeredAt));
    if (late) {
      throw new AnswerTooLate();
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

  // INV-38: a request or an answer carries at most 10 pictures, each an https link
  // (security review SEC-07)
  private static void ensurePicturesMayBeAttached(List<URI> pictures) {
    if (pictures.size() > MAX_PICTURES) {
      throw new TooManyPictures();
    }
    if (!pictures.stream().allMatch(HelpRequest::isHttpsLink)) {
      throw new PictureLinkNotSecure();
    }
  }

  private static boolean isHttpsLink(URI picture) {
    return "https".equalsIgnoreCase(picture.getScheme());
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

  public IngredientId ingredient() {
    return ingredient;
  }

  public String ingredientName() {
    return ingredientName;
  }

  public String substituteRequirements() {
    return substituteRequirements;
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

  public List<Help> helps() {
    return List.copyOf(helps);
  }

  public List<Note> notes() {
    return List.copyOf(notes);
  }

  /** Hands over the recorded events once; the application service publishes them. */
  public List<HelpRequestEvent> pullEvents() {
    List<HelpRequestEvent> pulled = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return pulled;
  }
}
