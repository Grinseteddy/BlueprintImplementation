package org.larder.quality;

import java.util.List;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

/** The channels the service publishes on, and the quality tests' queue on each. */
final class ServiceChannels {

  static final String CREATED = "cooking-assistance.help-request.created";
  static final String PROVIDED = "cooking-assistance.help.provided";
  static final String CANCELLED = "cooking-assistance.help-request.cancelled";
  static final String TIMED_OUT = "cooking-assistance.help-request.timed-out";

  /** Every send channel: publishing is mandatory, an unbound one would hold back the request. */
  static final List<String> SEND =
      List.of(
          CREATED,
          PROVIDED,
          "cooking-assistance.help.accepted",
          "cooking-assistance.help.rejected",
          CANCELLED,
          TIMED_OUT);

  private ServiceChannels() {}

  /** A queue on every send channel, so that no publication is held back; each one emptied. */
  static void listenToEvery(AmqpAdmin admin) {
    SEND.forEach(channel -> listenTo(admin, channel));
  }

  /** The test's queue on the channel, bound to the service's exchange. */
  static String queueOn(String channel) {
    return "quality." + channel;
  }

  private static void listenTo(AmqpAdmin admin, String channel) {
    String queue = queueOn(channel);
    admin.declareQueue(new Queue(queue, false, false, false));
    admin.declareBinding(
        BindingBuilder.bind(new Queue(queue))
            .to(new TopicExchange("cooking-assistance"))
            .with(channel));
    admin.purgeQueue(queue, false);
  }
}
