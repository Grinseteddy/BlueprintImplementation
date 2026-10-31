package org.larder.cookingassistance.adapter.out.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.larder.cookingassistance.application.IdGenerator;

/** Time-ordered UUIDv7 identities (RFC 9562, ADR0011), from the injected clock. */
public class UuidV7IdGenerator implements IdGenerator {

  private static final long VERSION_7 = 0x7000L;
  private static final long IETF_VARIANT = 0x8000_0000_0000_0000L;

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public UuidV7IdGenerator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public UUID next() {
    long unixMillis = clock.millis();
    long mostSignificant = (unixMillis << 16) | VERSION_7 | (random.nextLong() & 0x0FFFL);
    long leastSignificant = IETF_VARIANT | (random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL);
    return new UUID(mostSignificant, leastSignificant);
  }
}
