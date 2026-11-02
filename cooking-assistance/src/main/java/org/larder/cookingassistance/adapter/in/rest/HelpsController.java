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
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.domain.helprequest.HelpId;
import org.larder.cookingassistance.domain.helprequest.HelpRequestId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Operations of tag Helps (cooking-assistance.openapi.yaml). */
@RestController
class HelpsController implements HelpsApi {

  private final ProvideHelp provideHelp;
  private final String publicBaseUrl;

  HelpsController(ProvideHelp provideHelp, @PublicBaseUrl String publicBaseUrl) {
    this.provideHelp = provideHelp;
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

  @Override
  public ResponseEntity<HelpRequestLink> acceptHelp(
      UUID helpRequestId, UUID helpId, String version) {
    throw new NotImplemented("acceptHelp");
  }

  @Override
  public ResponseEntity<Help> getHelpOfHelpRequest(
      UUID helpRequestId, UUID helpId, String version) {
    throw new NotImplemented("getHelpOfHelpRequest");
  }

  @Override
  public ResponseEntity<HelpPage> listHelps(
      String version, UUID helpProvider, HelpType type, Integer limit, String cursor) {
    throw new NotImplemented("listHelps");
  }

  @Override
  public ResponseEntity<HelpPage> listHelpsOfHelpRequest(
      UUID helpRequestId, String version, Integer limit, String cursor) {
    throw new NotImplemented("listHelpsOfHelpRequest");
  }

  @Override
  public ResponseEntity<HelpRequestLink> rejectHelp(
      UUID helpRequestId, UUID helpId, String version, Rejection rejection) {
    throw new NotImplemented("rejectHelp");
  }
}
