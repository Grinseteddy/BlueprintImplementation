package org.larder.cookingassistance.adapter.in.rest;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.larder.cookingassistance.adapter.in.rest.generated.model.AskedProvider;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Cancellation;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequest;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestPage;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestStatus;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Note;
import org.larder.cookingassistance.adapter.in.rest.generated.model.ProviderState;
import org.larder.cookingassistance.application.Actor;
import org.larder.cookingassistance.application.CancelHelpRequest;
import org.larder.cookingassistance.application.HelpRequestFilter;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.application.Page;
import org.larder.cookingassistance.application.PreviousNote;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.domain.helprequest.CookId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.larder.cookingassistance.domain.helprequest.HowToStepId;
import org.larder.cookingassistance.domain.helprequest.RecipeId;
import org.springframework.lang.Nullable;

/** Maps the Help Requests DTOs of the OpenAPI contract to commands and results to responses. */
final class HelpRequestMapper {

  private HelpRequestMapper() {}

  static RequestHelpWithPreparationStep.Command toCommand(Actor actor, HelpRequestCreate body) {
    return new RequestHelpWithPreparationStep.Command(
        actor,
        body.getTitle(),
        body.getDescription(),
        new RecipeId(body.getRecipe()),
        new HowToStepId(body.getHowToStep()),
        body.getPreferredProvider().stream()
            .map(HelpVocabulary::toDomain)
            .collect(Collectors.toSet()));
  }

  static CancelHelpRequest.Command toCommand(
      Actor actor, HelpRequestId helpRequestId, Cancellation body) {
    return new CancelHelpRequest.Command(
        actor, helpRequestId, Optional.ofNullable(body).map(Cancellation::getNote));
  }

  static HelpRequestLink toLink(String publicBaseUrl, HelpRequestId id) {
    return new HelpRequestLink(
        id.value(), Links.absolute(publicBaseUrl, "/help-requests/{helpRequestId}", id.value()));
  }

  /** An absent query parameter does not narrow the list. */
  static HelpRequestFilter toFilter(
      @Nullable UUID requester,
      @Nullable HelpRequestStatus status,
      @Nullable org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType type) {
    return new HelpRequestFilter(
        Optional.ofNullable(requester).map(CookId::new),
        Optional.ofNullable(status).map(HelpRequestMapper::domainStatus),
        Optional.ofNullable(type).map(HelpVocabulary::toDomain));
  }

  static HelpRequestPage toPage(Page<HelpRequestView> page, String publicBaseUrl) {
    return new HelpRequestPage(
            page.items().stream().map(view -> toResponse(view, publicBaseUrl)).toList())
        .nextCursor(Cursors.next(page, view -> view.id().value()));
  }

  static HelpRequest toResponse(HelpRequestView view, String publicBaseUrl) {
    List<AskedProvider> asked =
        view.askedProviders().stream()
            .map(
                provider ->
                    new AskedProvider(
                        HelpVocabulary.toContract(provider.providerType()),
                        provider.deadline().atOffset(ZoneOffset.UTC),
                        ProviderState.fromValue(provider.state().name())))
            .toList();
    HelpRequest response =
        new HelpRequest(
                view.id().value(),
                view.requester().value(),
                view.title(),
                HelpVocabulary.toContract(view.type()),
                view.description(),
                view.preferredProviders().stream()
                    .map(HelpVocabulary::toContract)
                    .collect(Collectors.toSet()),
                asked,
                HelpRequestStatus.fromValue(view.status().name()))
            .recipe(view.recipe().value())
            .howToStep(view.howToStep().value())
            .helpsLink(
                Links.absolute(
                    publicBaseUrl, "/help-requests/{helpRequestId}/helps", view.id().value()));
    view.acceptedHelp()
        .ifPresent(
            help ->
                response.acceptedHelpLink(
                    HelpMapper.toLink(publicBaseUrl, view.id(), help).getHelpLink()));
    return response
        .suggestedProviders(
            OptionalArrays.absentWhenEmpty(
                view.suggestedProviders().stream().map(HelpVocabulary::toContract).toList()))
        .notes(
            OptionalArrays.absentWhenEmpty(
                view.notes().stream().map(HelpRequestMapper::toNote).toList()))
        .previousNotes(
            OptionalArrays.absentWhenEmpty(
                view.previousNotes().stream()
                    .map(previous -> toPreviousNote(publicBaseUrl, previous))
                    .toList()));
  }

  private static Note toNote(org.larder.cookingassistance.domain.helprequest.Note note) {
    return new Note(
        Note.KindEnum.fromValue(note.kind().name()),
        note.text(),
        note.writtenAt().atOffset(ZoneOffset.UTC));
  }

  /** A note of an earlier request links to the request it was written on. */
  private static Note toPreviousNote(String publicBaseUrl, PreviousNote previous) {
    return toNote(previous.note())
        .helpRequestLink(toLink(publicBaseUrl, previous.helpRequest()).getHelpRequestLink());
  }

  private static org.larder.cookingassistance.domain.helprequest.HelpRequestStatus domainStatus(
      HelpRequestStatus status) {
    return org.larder.cookingassistance.domain.helprequest.HelpRequestStatus.valueOf(status.name());
  }
}
