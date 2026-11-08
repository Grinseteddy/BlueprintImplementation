package org.larder.cookingassistance.adapter.out.persistence;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** The current span as W3C traceparent (ADR0020); none without a tracer, a propagator or a span. */
@Component
class MicrometerCurrentTrace implements CurrentTrace {

  private final ObjectProvider<Tracer> tracer;
  private final ObjectProvider<Propagator> propagator;

  MicrometerCurrentTrace(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public Optional<String> traceparent() {
    Tracer currentTracer = tracer.getIfAvailable();
    Propagator w3c = propagator.getIfAvailable();
    Span span = currentTracer == null ? null : currentTracer.currentSpan();
    if (span == null || w3c == null) {
      return Optional.empty();
    }
    // the bridge's propagator writes the W3C header; no format of our own
    Map<String, String> carrier = new HashMap<>();
    w3c.inject(span.context(), carrier, Map::put);
    return Optional.ofNullable(carrier.get("traceparent"));
  }
}
