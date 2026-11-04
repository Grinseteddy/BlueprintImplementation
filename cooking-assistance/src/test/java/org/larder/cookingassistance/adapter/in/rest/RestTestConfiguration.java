package org.larder.cookingassistance.adapter.in.rest;

import java.util.Optional;
import org.larder.cookingassistance.application.AcceptHelp;
import org.larder.cookingassistance.application.CancelHelpRequest;
import org.larder.cookingassistance.application.HelpRequestQueries;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.application.RejectHelp;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.application.fakes.InMemoryHelpRequestRepository;
import org.larder.cookingassistance.fixtures.HelpRequests;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** The step 8 use cases on the contract-checked fake, with fixed ids and clock: no database. */
@TestConfiguration
class RestTestConfiguration {

  @Bean
  InMemoryHelpRequestRepository helpRequests() {
    return new InMemoryHelpRequestRepository();
  }

  @Bean
  RequestHelpWithPreparationStep requestHelpWithPreparationStep(
      InMemoryHelpRequestRepository helpRequests) {
    return new RequestHelpWithPreparationStep(
        helpRequests, () -> HelpRequests.HELP_REQUEST_ID.value(), HelpRequests.FRIDAY_18_00);
  }

  /** The slice tests read nothing; the read side is tested against PostgreSQL (step 11). */
  @Bean
  HelpRequestQueries noHelpRequests() {
    return id -> Optional.empty();
  }

  @Bean
  ProvideHelp provideHelp(InMemoryHelpRequestRepository helpRequests) {
    return new ProvideHelp(
        helpRequests, () -> HelpRequests.HELP_ID.value(), HelpRequests.FRIDAY_18_10);
  }

  @Bean
  CancelHelpRequest cancelHelpRequest(InMemoryHelpRequestRepository helpRequests) {
    return new CancelHelpRequest(helpRequests, HelpRequests.FRIDAY_18_10);
  }

  @Bean
  AcceptHelp acceptHelp(InMemoryHelpRequestRepository helpRequests) {
    return new AcceptHelp(helpRequests, HelpRequests.FRIDAY_18_10);
  }

  @Bean
  RejectHelp rejectHelp(InMemoryHelpRequestRepository helpRequests) {
    return new RejectHelp(helpRequests, HelpRequests.FRIDAY_18_10);
  }
}
