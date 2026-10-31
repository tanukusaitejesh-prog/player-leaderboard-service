package dev.tejesh.leaderboard.outbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Publishes committed outbox rows to Kafka. Rows are marked published only after the broker acks,
 * so a crash between the ack and the commit re-sends them: delivery is at-least-once.
 */
@Component
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionOperations transactions;
  private final int batchSize;
  private final long ackTimeoutMs;

  public OutboxPublisher(
      OutboxRepository outbox,
      KafkaTemplate<String, String> kafka,
      TransactionOperations transactions,
      @Value("${outbox.batch-size:100}") int batchSize,
      @Value("${outbox.ack-timeout-ms:15000}") long ackTimeoutMs) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.transactions = transactions;
    this.batchSize = batchSize;
    this.ackTimeoutMs = ackTimeoutMs;
  }

  @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:200}")
  public void poll() {
    publishBatch();
  }

  /** Returns the number of rows published. */
  public int publishBatch() {
    return transactions.execute(status -> publishLockedBatch());
  }

  private int publishLockedBatch() {
    List<OutboxEvent> batch = outbox.lockUnpublished(batchSize);
    if (batch.isEmpty()) {
      return 0;
    }

    List<UUID> acked = awaitAcks(sendAll(batch));
    if (!acked.isEmpty()) {
      outbox.markPublished(acked);
    }
    return acked.size();
  }

  private Map<UUID, CompletableFuture<SendResult<String, String>>> sendAll(
      List<OutboxEvent> batch) {
    Map<UUID, CompletableFuture<SendResult<String, String>>> sends = new LinkedHashMap<>();
    for (OutboxEvent event : batch) {
      try {
        sends.put(event.id(), kafka.send(event.topic(), event.eventKey(), event.payload()));
      } catch (RuntimeException e) {
        // The broker is unreachable; the rest would each block for max.block.ms too.
        log.warn("Stopping outbox batch, send failed for event {}", event.id(), e);
        break;
      }
    }
    return sends;
  }

  private List<UUID> awaitAcks(Map<UUID, CompletableFuture<SendResult<String, String>>> sends) {
    List<UUID> acked = new ArrayList<>(sends.size());
    for (Map.Entry<UUID, CompletableFuture<SendResult<String, String>>> send : sends.entrySet()) {
      try {
        send.getValue().get(ackTimeoutMs, TimeUnit.MILLISECONDS);
        acked.add(send.getKey());
      } catch (ExecutionException | TimeoutException e) {
        log.warn("No ack for outbox event {}, will retry", send.getKey(), e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return acked;
  }
}
