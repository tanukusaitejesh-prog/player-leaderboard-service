package dev.tejesh.leaderboard.outbox;

import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
    UUID id, String topic, String eventKey, String payload, String traceParent) {

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private UUID id;
    private String topic;
    private String eventKey;
    private String payload;
    private String traceParent;

    private Builder() {}

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder topic(String topic) {
      this.topic = topic;
      return this;
    }

    public Builder eventKey(String eventKey) {
      this.eventKey = eventKey;
      return this;
    }

    public Builder payload(String payload) {
      this.payload = payload;
      return this;
    }

    public Builder traceParent(String traceParent) {
      this.traceParent = traceParent;
      return this;
    }

    public OutboxEvent build() {
      return new OutboxEvent(
          Objects.requireNonNull(id, "id"),
          Objects.requireNonNull(topic, "topic"),
          Objects.requireNonNull(eventKey, "eventKey"),
          Objects.requireNonNull(payload, "payload"),
          traceParent);
    }
  }
}
