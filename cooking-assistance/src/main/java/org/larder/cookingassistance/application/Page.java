package org.larder.cookingassistance.application;

import java.util.List;

/** One page of a list, oldest first; hasMore when a later page exists (ADR0016 L6). */
public record Page<T>(List<T> items, boolean hasMore) {}
