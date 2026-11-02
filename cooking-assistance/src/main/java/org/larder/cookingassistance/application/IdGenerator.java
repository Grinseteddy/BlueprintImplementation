package org.larder.cookingassistance.application;

import java.util.UUID;

/** Generates new identities (UUIDv7, ADR0011). */
public interface IdGenerator {

  UUID next();
}
