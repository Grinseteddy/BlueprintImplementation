package org.larder.cookingassistance.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Coverage gate: every receive operation of the AsyncAPI spec has a registered consumer. */
class ConsumerCoverageTest {

  @DisplayName("the receive operations of cooking-assistance.asyncapi.yaml equal the registered consumers (consumeGrandmaHelpProvided) -- B3-S02 US-CA-03 ADR0014")
  @Test
  void everyReceiveOperationHasAConsumer() throws Exception {
    assertThat(MessageConsumers.BY_RECEIVE_OPERATION.keySet())
        .containsExactlyInAnyOrderElementsOf(receiveOperations());
  }

  private static List<String> receiveOperations() throws Exception {
    try (InputStream in =
        ConsumerCoverageTest.class
            .getClassLoader()
            .getResourceAsStream("api/cooking-assistance.asyncapi.yaml")) {
      JsonNode operations = new ObjectMapper(new YAMLFactory()).readTree(in).get("operations");
      List<String> receive = new ArrayList<>();
      operations
          .fields()
          .forEachRemaining(
              operation -> {
                if ("receive".equals(operation.getValue().path("action").asText())) {
                  receive.add(operation.getKey());
                }
              });
      return receive;
    }
  }
}
