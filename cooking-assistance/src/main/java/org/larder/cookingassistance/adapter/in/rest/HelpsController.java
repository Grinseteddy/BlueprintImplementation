package org.larder.cookingassistance.adapter.in.rest;

import java.util.UUID;
import org.larder.cookingassistance.adapter.in.rest.generated.HelpsApi;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Help;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpPage;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Rejection;
import org.larder.cookingassistance.application.AcceptHelp;
import org.larder.cookingassistance.application.HelpNotFound;
import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.HelpRequestQueries;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.application.RejectHelp;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Operations of tag Helps (cooking-assistance.openapi.yaml). */
@RestController
class HelpsController implements HelpsApi {

  private final ProvideHelp provideHelp;
  private final AcceptHelp acceptHelp;
  private final RejectHelp rejectHelp;
  private final HelpRequestQueries queries;
  private final String publicBaseUrl;

  HelpsController(
      ProvideHelp provideHelp,
      AcceptHelp acceptHelp,
      RejectHelp rejectHelp,
      HelpRequestQueries queries,
      @PublicBaseUrl String publicBaseUrl) {
    this.provideHelp = provideHelp;
    this.acceptHelp = acceptHelp;
    this.rejectHelp = rejectHelp;
    this.queries = queries;
    this.publicBaseUrl = publicBaseUrl;
  }

  // op: provideHelp (cooking-assistance.openapi.yaml) · cmd: Provide Help
  @Override
  public ResponseEntity<HelpLink> provideHelp(
      UUID helpRequestId, String version, HelpCreate helpCreate) {
    HelpRequestId requestId = new HelpRequestId(helpRequestId);
    HelpId helpId =
        provideHelp.execute(HelpMapper.toCommand(CurrentActor.get(), requestId, helpCreate));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(HelpMapper.toLink(publicBaseUrl, requestId, helpId));
  }

  // op: acceptHelp (cooking-assistance.openapi.yaml) · cmd: Accept help
  @Override
  public ResponseEntity<HelpRequestLink> acceptHelp(
      UUID helpRequestId, UUID helpId, String version) {
    HelpRequestId requestId = new HelpRequestId(helpRequestId);
    acceptHelp.execute(HelpMapper.toAcceptance(CurrentActor.get(), requestId, new HelpId(helpId)));
    return ResponseEntity.ok(HelpRequestMapper.toLink(publicBaseUrl, requestId));
  }

  // op: getHelpOfHelpRequest (cooking-assistance.openapi.yaml) · RM: help (aggregate tables,
  // ADR0016)
  @Override
  public ResponseEntity<Help> getHelpOfHelpRequest(
      UUID helpRequestId, UUID helpId, String version) {
    HelpRequestId request = new HelpRequestId(helpRequestId);
    HelpId help = new HelpId(helpId);
    return ResponseEntity.ok(
        HelpMapper.toResponse(
            queries.helpOf(request, help).orElseThrow(() -> new HelpNotFound(request, help))));
  }

  // op: listHelps (cooking-assistance.openapi.yaml) · RM: help (aggregate tables, ADR0016 L6)
  @Override
  public ResponseEntity<HelpPage> listHelps(
      String version, UUID helpProvider, HelpType type, Integer limit, String cursor) {
    return ResponseEntity.ok(
        HelpMapper.toPage(
            queries.helps(
                HelpMapper.toFilter(helpProvider, type),
                limit,
                Cursors.after(cursor).map(HelpId::new))));
  }

  // op: listHelpsOfHelpRequest (cooking-assistance.openapi.yaml) · RM: help (aggregate tables,
  // ADR0016 L6)
  @Override
  public ResponseEntity<HelpPage> listHelpsOfHelpRequest(
      UUID helpRequestId, String version, Integer limit, String cursor) {
    HelpRequestId id = new HelpRequestId(helpRequestId);
    return ResponseEntity.ok(
        HelpMapper.toPage(
            queries
                .helpsOf(id, limit, Cursors.after(cursor).map(HelpId::new))
                .orElseThrow(() -> new HelpRequestNotFound(id))));
  }

  // op: rejectHelp (cooking-assistance.openapi.yaml) · cmd: Reject help
  @Override
  public ResponseEntity<HelpRequestLink> rejectHelp(
      UUID helpRequestId, UUID helpId, String version, Rejection rejection) {
    HelpRequestId requestId = new HelpRequestId(helpRequestId);
    rejectHelp.execute(
        HelpMapper.toRejection(CurrentActor.get(), requestId, new HelpId(helpId), rejection));
    return ResponseEntity.ok(HelpRequestMapper.toLink(publicBaseUrl, requestId));
  }
}
