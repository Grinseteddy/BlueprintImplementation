package org.larder.cookingassistance.adapter.in.rest;

import java.util.UUID;
import org.larder.cookingassistance.adapter.in.rest.generated.HelpRequestsApi;
import org.larder.cookingassistance.adapter.in.rest.generated.model.Cancellation;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequest;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestCreate;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestLink;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestPage;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpRequestStatus;
import org.larder.cookingassistance.adapter.in.rest.generated.model.HelpType;
import org.larder.cookingassistance.application.CancelHelpRequest;
import org.larder.cookingassistance.application.HelpRequestNotFound;
import org.larder.cookingassistance.application.HelpRequestQueries;
import org.larder.cookingassistance.application.HelpRequestView;
import org.larder.cookingassistance.application.Page;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Operations of tag Help Requests (cooking-assistance.openapi.yaml). */
@RestController
class HelpRequestsController implements HelpRequestsApi {

  private final RequestHelpWithPreparationStep requestHelpWithPreparationStep;
  private final CancelHelpRequest cancelHelpRequest;
  private final HelpRequestQueries queries;
  private final String publicBaseUrl;

  HelpRequestsController(
      RequestHelpWithPreparationStep requestHelpWithPreparationStep,
      CancelHelpRequest cancelHelpRequest,
      HelpRequestQueries queries,
      @PublicBaseUrl String publicBaseUrl) {
    this.requestHelpWithPreparationStep = requestHelpWithPreparationStep;
    this.cancelHelpRequest = cancelHelpRequest;
    this.queries = queries;
    this.publicBaseUrl = publicBaseUrl;
  }

  // op: createHelpRequest (cooking-assistance.openapi.yaml) · cmd: Request help Preparation step
  @Override
  public ResponseEntity<HelpRequestLink> createHelpRequest(
      String version, HelpRequestCreate helpRequestCreate) {
    HelpRequestId id =
        switch (helpRequestCreate.getType()) {
          case PREPARATION_STEP_EXPLANATION ->
              requestHelpWithPreparationStep.execute(
                  HelpRequestMapper.toCommand(CurrentActor.get(), helpRequestCreate));
          default -> throw new NotImplemented("createHelpRequest " + helpRequestCreate.getType());
        };
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(HelpRequestMapper.toLink(publicBaseUrl, id));
  }

  // op: getHelpRequestById (cooking-assistance.openapi.yaml) · RM: help request (aggregate tables,
  // ADR0016)
  @Override
  public ResponseEntity<HelpRequest> getHelpRequestById(UUID helpRequestId, String version) {
    HelpRequestId id = new HelpRequestId(helpRequestId);
    return ResponseEntity.ok(
        HelpRequestMapper.toResponse(
            queries.byId(id).orElseThrow(() -> new HelpRequestNotFound(id)), publicBaseUrl));
  }

  // op: cancelHelpRequest (cooking-assistance.openapi.yaml) · cmd: Cancel help request
  @Override
  public ResponseEntity<HelpRequestLink> cancelHelpRequest(
      UUID helpRequestId, String version, Cancellation cancellation) {
    HelpRequestId id = new HelpRequestId(helpRequestId);
    cancelHelpRequest.execute(HelpRequestMapper.toCommand(CurrentActor.get(), id, cancellation));
    return ResponseEntity.ok(HelpRequestMapper.toLink(publicBaseUrl, id));
  }

  // op: listHelpRequests (cooking-assistance.openapi.yaml) · RM: help request (aggregate tables,
  // ADR0016 L6)
  @Override
  public ResponseEntity<HelpRequestPage> listHelpRequests(
      String version,
      UUID requester,
      HelpRequestStatus status,
      HelpType type,
      Integer limit,
      String cursor) {
    Page<HelpRequestView> page =
        queries.list(
            HelpRequestMapper.toFilter(requester, status, type),
            limit,
            Cursors.after(cursor).map(HelpRequestId::new));
    return ResponseEntity.ok(HelpRequestMapper.toPage(page, publicBaseUrl));
  }
}
