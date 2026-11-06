package org.larder.cookingassistance.adapter.in.rest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.larder.cookingassistance.application.Page;
import org.springframework.lang.Nullable;

/** The opaque nextCursor of the lists: base64url of the last item's id (ADR0016 L6). */
final class Cursors {

  private Cursors() {}

  /** The nextCursor is absent on the last page; otherwise it points after the page's last item. */
  @Nullable
  static <T> String next(Page<T> page, Function<T, UUID> id) {
    return page.hasMore() ? encode(id.apply(page.items().getLast())) : null;
  }

  private static String encode(UUID last) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(last.toString().getBytes(StandardCharsets.UTF_8));
  }

  /** The id a page starts after; the cursor is omitted for the first page. */
  static Optional<UUID> after(@Nullable String cursor) {
    return Optional.ofNullable(cursor).map(Cursors::decode);
  }

  private static UUID decode(String cursor) {
    try {
      return UUID.fromString(
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8));
    } catch (IllegalArgumentException notIssued) {
      throw new InvalidCursor();
    }
  }
}
