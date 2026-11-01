package org.larder.cookingassistance.adapter.in.rest;

import org.larder.cookingassistance.application.ProvideHelp;
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

  @Bean
  ProvideHelp provideHelp(InMemoryHelpRequestRepository helpRequests) {
    return new ProvideHelp(
        helpRequests, () -> HelpRequests.HELP_ID.value(), HelpRequests.FRIDAY_18_10);
  }
}
