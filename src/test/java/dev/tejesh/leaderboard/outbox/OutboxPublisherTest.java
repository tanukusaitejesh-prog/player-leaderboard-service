package dev.tejesh.leaderboard.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionOperations;

class OutboxPublisherTest {

  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

  private final OutboxPublisher publisher =
      new OutboxPublisher(
          outbox, kafka, TransactionOperations.withoutTransaction(), Tracer.NOOP, meters, 100, 50);

  private final OutboxEvent first = event("a");
  private final OutboxEvent second = event("b");

  private static OutboxEvent event(String key) {
    return OutboxEvent.builder()
        .id(UUID.randomUUID())
        .topic("t")
        .eventKey(key)
        .payload("{}")
        .build();
  }

  private static CompletableFuture<SendResult<String, String>> acked() {
    return CompletableFuture.completedFuture(null);
  }

  @Test
  void emptyBatchDoesNothing() {
    when(outbox.lockUnpublished(100)).thenReturn(List.of());

    assertThat(publisher.publishBatch()).isZero();

    verify(outbox, never()).markPublished(any());
  }

  @Test
  void marksEveryAckedEventPublished() {
    when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(first, second));
    when(kafka.send("t", "a", "{}")).thenReturn(acked());
    when(kafka.send("t", "b", "{}")).thenReturn(acked());

    assertThat(publisher.publishBatch()).isEqualTo(2);

    verify(outbox).markPublished(List.of(first.id(), second.id()));
  }

  @Test
  void failedAckLeavesThatEventUnpublished() {
    when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(first, second));
    when(kafka.send("t", "a", "{}"))
        .thenReturn(CompletableFuture.failedFuture(new KafkaException("nack")));
    when(kafka.send("t", "b", "{}")).thenReturn(acked());

    assertThat(publisher.publishBatch()).isEqualTo(1);

    verify(outbox).markPublished(List.of(second.id()));
    assertThat(meters.counter("outbox.publish.failures").count()).isEqualTo(1.0);
  }

  @Test
  void ackTimeoutLeavesEventUnpublished() {
    when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(first));
    when(kafka.send("t", "a", "{}")).thenReturn(new CompletableFuture<>());

    assertThat(publisher.publishBatch()).isZero();

    verify(outbox, never()).markPublished(any());
  }

  @Test
  void synchronousSendFailureStopsTheBatch() {
    when(outbox.lockUnpublished(anyInt())).thenReturn(List.of(first, second));
    when(kafka.send("t", "a", "{}")).thenThrow(new KafkaException("broker down"));

    assertThat(publisher.publishBatch()).isZero();

    verify(kafka, times(1)).send(any(String.class), any(String.class), any(String.class));
    assertThat(meters.counter("outbox.publish.failures").count()).isEqualTo(1.0);
    verify(outbox, never()).markPublished(any());
  }
}
