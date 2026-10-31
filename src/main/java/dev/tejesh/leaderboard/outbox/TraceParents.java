package dev.tejesh.leaderboard.outbox;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;

/** Carries a trace across the outbox table as a W3C traceparent string. */
public final class TraceParents {

  private TraceParents() {}

  /** Returns null when no trace is active, which is the case when tracing is off. */
  public static String current(Tracer tracer) {
    Span span = tracer.currentSpan();
    if (span == null) {
      return null;
    }
    TraceContext context = span.context();
    String traceId = context.traceId();
    if (traceId == null || traceId.isBlank()) {
      return null;
    }
    return "00-" + traceId + "-" + context.spanId() + "-01";
  }

  /** Starts a span under the stored parent, or a new root if there isn't a valid one. */
  static Span startChild(Tracer tracer, String traceParent, String name) {
    Span.Builder span = tracer.spanBuilder().name(name);
    String[] parts = traceParent == null ? new String[0] : traceParent.split("-");
    if (parts.length == 4) {
      span.setParent(
          tracer
              .traceContextBuilder()
              .traceId(parts[1])
              .spanId(parts[2])
              .sampled(Boolean.TRUE)
              .build());
    }
    return span.start();
  }
}
