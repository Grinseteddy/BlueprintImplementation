package org.larder.cookingassistance.adapter.out.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * The module's JSON conventions for AsyncAPI shapes (times as ISO-8601 text), shared by outbox
 * payloads and stored answers so both keep the same shape (ADR0015, ADR0009 addition step 7).
 */
final class AsyncApiJson {

  static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .addModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .build();

  private AsyncApiJson() {}
}
