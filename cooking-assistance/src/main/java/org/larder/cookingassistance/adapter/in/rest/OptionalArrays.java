package org.larder.cookingassistance.adapter.in.rest;

import java.util.List;
import org.springframework.lang.Nullable;

/** The spec's optional arrays are absent when there is nothing to show, never empty or null. */
final class OptionalArrays {

  private OptionalArrays() {}

  @Nullable
  static <T> List<T> absentWhenEmpty(List<T> items) {
    return items.isEmpty() ? null : items;
  }
}
