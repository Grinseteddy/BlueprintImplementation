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

  // INV-01: help with a preparation step comes from the Community or Grandma Avatar, never a Chef
  private static final List<HelpProviderType> PREPARATION_STEP_PROVIDERS =
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
            askedProviders,
            status,
            version);
    request.helps.addAll(helps);
    request.notes.addAll(notes);
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
    Instant answeredAt = clock.instant();
    ensureNotFinal();
    ensureProviderWasAsked(provider);
    ensureAnsweredInTime(provider, answeredAt);
    ensureNotOwnRequest(provider);
    ensurePicturesMayBeAttached(answer.images());
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
            answeredAt));
  }

  /** Each asked provider has its own state: only the answering provider's side is ANSWERED. */
  private void markSideAnswered(HelpProvider provider) {
    askedProviders =
        askedProviders.stream()
            .map(asked -> asked.isFor(provider) ? asked.answered() : asked)
            .toList();
  }

  // slice: B3-S03, story: US-CA-03, cmd: Cancel help request
  public void cancel(Optional<String> note, Clock clock) {
    ensureNotFinal();
    Instant cancelledAt = clock.instant();
    // INV-07, INV-08: the cancellation note is kept and shown to providers and on future requests
    note.ifPresent(text -> notes.add(Note.cancellation(text, cancelledAt)));
    status = HelpRequestStatus.CANCELLED;
    // evt: Help request cancelled
    pendingEvents.add(
        new HelpRequestCancelled(id, requester, note, askedProviders, status, cancelledAt));
  }

  // slice: B3-S04, story: US-CA-03, cmd: Accept help
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

  // slice: B3-S05, story: US-CA-03, cmd: Reject help
  public void reject(HelpId helpId, String note, Clock clock) {
    ensureAnswered();
    ensureNoteGiven(note);
    Help help = helpGivenToThisRequest(helpId);
    ensureNotRejected(help);
    Instant rejectedAt = clock.instant();
    // INV-06, INV-07: what didn't work is kept and shown to providers and on future requests
    notes.add(Note.rejection(note, rejectedAt, helpId));
    replaceHelp(help, help.rejected());
    askAgain(rejectedAt);
    status = HelpRequestStatus.OPEN;
    // evt: Help rejected
    pendingEvents.add(
        new HelpRejected(id, requester, helpId, note, askedProviders, status, rejectedAt));
  }

  // INV-06: sent again to the same providers, each with a new 12 h deadline from the rejection
  private void askAgain(Instant rejectedAt) {
    Instant newDeadline = rejectedAt.plus(PREPARATION_STEP_ANSWER_TIME);
    askedProviders = askedProviders.stream().map(asked -> asked.askedAgain(newDeadline)).toList();
  }

  /** A Help is immutable: marking it swaps it for its marked copy in this request. */
  private void replaceHelp(Help help, Help marked) {
    helps.replaceAll(candidate -> candidate == help ? marked : candidate);
  }

  // INV-06: a rejection says what didn't work (step 3 r5)
  private static void ensureNoteGiven(String note) {
    if (note == null || note.isBlank()) {
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

  // slice: B3-S06, story: US-CA-03, cmd: Time out help request (clock)
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
   * INV-09: the providers suggested to the requester after a time-out (event and read side): the
   * preparation-step providers not yet asked, once every asked provider has timed out.
   */
  public static List<HelpProviderType> suggestionsAfterTimeOut(List<AskedProvider> asked) {
    if (asked.stream().anyMatch(provider -> provider.state() != ProviderState.TIMED_OUT)) {
      return List.of();
    }
    List<HelpProviderType> askedTypes = asked.stream().map(AskedProvider::providerType).toList();
    return PREPARATION_STEP_PROVIDERS.stream()
        .filter(provider -> !askedTypes.contains(provider))
        .toList();
  }

  // INV-09: a request times out only after every asked provider's deadline (step 3 r5)
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

  // INV-09: an answer counts at or before the provider's deadline, not after it (step 3 r5)
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
