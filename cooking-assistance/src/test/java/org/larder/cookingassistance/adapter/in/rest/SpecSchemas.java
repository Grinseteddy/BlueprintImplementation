package org.larder.cookingassistance.adapter.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;

/** Checks response bodies against the component schemas of the approved OpenAPI spec. */
public final class SpecSchemas {

  private static final OpenApiContract CONTRACT = new OpenApiContract();

  private SpecSchemas() {}

  public static void assertMatches(String json, String schemaName) {
    assertThat(
            CONTRACT.schema("/components/schemas/" + schemaName).validate(json, InputFormat.JSON))
        .as("%s against %s", json, schemaName)
        .isEmpty();
  }
}
