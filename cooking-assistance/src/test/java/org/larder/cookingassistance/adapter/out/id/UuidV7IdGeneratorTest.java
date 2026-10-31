package org.larder.cookingassistance.adapter.out.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** UUIDv7 identities (ADR0011) — added on the way in step 8, increment 0. */
class UuidV7IdGeneratorTest {

  @DisplayName("a generated id has version 7 and the IETF variant (2) -- B3-S01,B3-S02 US-CA-03 ADR0011")
  @Test
  void generatedIdIsVersion7WithIetfVariant() {
    UUID id = new UuidV7IdGenerator(Clock.fixed(Instant.parse("2026-11-06T17:00:00Z"), ZoneOffset.UTC)).next();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @DisplayName("an id generated at a later clock instant sorts after one generated earlier -- B3-S01,B3-S02 US-CA-03 ADR0011")
  @Test
  void idsFromLaterInstantsSortAfterEarlierOnes() {
    Instant friday = Instant.parse("2026-11-06T17:00:00Z");
    UUID earlier = new UuidV7IdGenerator(Clock.fixed(friday, ZoneOffset.UTC)).next();
    UUID later =
        new UuidV7IdGenerator(Clock.fixed(friday.plusMillis(1), ZoneOffset.UTC)).next();

    assertThat(later).isGreaterThan(earlier);
  }
}
