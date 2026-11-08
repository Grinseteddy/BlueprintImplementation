package org.larder.cookingassistance.adapter.config;

import java.time.Clock;
import org.larder.cookingassistance.adapter.out.id.UuidV7IdGenerator;
import org.larder.cookingassistance.application.AcceptHelp;
import org.larder.cookingassistance.application.CancelHelpRequest;
import org.larder.cookingassistance.application.IdGenerator;
import org.larder.cookingassistance.application.ProcessedMessages;
import org.larder.cookingassistance.application.ProvideGrandmaAvatarHelp;
import org.larder.cookingassistance.application.ProvideHelp;
import org.larder.cookingassistance.application.RejectHelp;
import org.larder.cookingassistance.application.RequestHelpWithPreparationStep;
import org.larder.cookingassistance.application.TimeOutDueHelpRequests;
import org.larder.cookingassistance.application.UnitOfWork;
import org.larder.cookingassistance.domain.helprequest.HelpRequestRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the module's application layer to its adapters; the application classes stay plain. */
@Configuration
class ApplicationConfiguration {

  /** ADR0021: one run (one unit of work) times out at most this many due requests. */
  static final int TIME_OUT_BATCH_SIZE = 100;

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  IdGenerator idGenerator(Clock clock) {
    return new UuidV7IdGenerator(clock);
  }

  @Bean
  RequestHelpWithPreparationStep requestHelpWithPreparationStep(
      HelpRequestRepository helpRequests, IdGenerator ids, Clock clock) {
    return new RequestHelpWithPreparationStep(helpRequests, ids, clock);
  }

  @Bean
  ProvideHelp provideHelp(HelpRequestRepository helpRequests, IdGenerator ids, Clock clock) {
    return new ProvideHelp(helpRequests, ids, clock);
  }

  @Bean
  ProvideGrandmaAvatarHelp provideGrandmaAvatarHelp(
      HelpRequestRepository helpRequests,
      ProcessedMessages processedMessages,
      UnitOfWork unitOfWork,
      IdGenerator ids,
      Clock clock) {
    return new ProvideGrandmaAvatarHelp(helpRequests, processedMessages, unitOfWork, ids, clock);
  }

  @Bean
  CancelHelpRequest cancelHelpRequest(HelpRequestRepository helpRequests, Clock clock) {
    return new CancelHelpRequest(helpRequests, clock);
  }

  @Bean
  AcceptHelp acceptHelp(HelpRequestRepository helpRequests, Clock clock) {
    return new AcceptHelp(helpRequests, clock);
  }

  @Bean
  RejectHelp rejectHelp(HelpRequestRepository helpRequests, Clock clock) {
    return new RejectHelp(helpRequests, clock);
  }

  @Bean
  TimeOutDueHelpRequests timeOutDueHelpRequests(
      HelpRequestRepository helpRequests, UnitOfWork unitOfWork, Clock clock) {
    return new TimeOutDueHelpRequests(helpRequests, unitOfWork, clock, TIME_OUT_BATCH_SIZE);
  }
}
