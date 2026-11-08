package org.larder.cookingassistance.adapter.out.persistence;

import java.util.Optional;

/** The W3C traceparent of the trace the current command runs in, if any (ADR0020). */
interface CurrentTrace {

  Optional<String> traceparent();
}
