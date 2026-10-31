package dev.tejesh.leaderboard.config;

import dev.tejesh.leaderboard.score.ScoreSubmittedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfig {

  @Bean
  public NewTopic scoreEventsTopic(@Value("${kafka.score-events.partitions:6}") int partitions) {
    return TopicBuilder.name(KafkaTopics.SCORE_EVENTS).partitions(partitions).replicas(1).build();
  }

  /** Same partition count: the recoverer keeps each failed record on its original partition. */
  @Bean
  public NewTopic scoreEventsDltTopic(@Value("${kafka.score-events.partitions:6}") int partitions) {
    return TopicBuilder.name(KafkaTopics.SCORE_EVENTS_DLT)
        .partitions(partitions)
        .replicas(1)
        .build();
  }

  /** Boot applies this bean to the listener container factory. */
  @Bean
  public DefaultErrorHandler kafkaErrorHandler(
      ProducerFactory<?, ?> producerFactory, MeterRegistry registry) {
    DeadLetterPublishingRecoverer deadLetters =
        new DeadLetterPublishingRecoverer(
            deadLetterTemplate(producerFactory),
            (record, ex) -> new TopicPartition(KafkaTopics.SCORE_EVENTS_DLT, record.partition()));
    Counter deadLettered = registry.counter("leaderboard.consumer.deadlettered");

    ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
    backOff.setInitialInterval(200);
    backOff.setMultiplier(2.0);

    DefaultErrorHandler handler =
        new DefaultErrorHandler(
            (record, ex) -> {
              deadLetters.accept(record, ex);
              deadLettered.increment();
            },
            backOff);
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    return handler;
  }

  /**
   * Not a bean: a second KafkaTemplate bean would switch off Boot's auto-configured one. A record
   * that failed deserialization reaches the DLT as its original bytes, anything else as JSON.
   */
  private static KafkaTemplate<String, Object> deadLetterTemplate(ProducerFactory<?, ?> source) {
    Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
    serializers.put(byte[].class, new ByteArraySerializer());
    serializers.put(ScoreSubmittedEvent.class, new JsonSerializer<ScoreSubmittedEvent>());
    return new KafkaTemplate<>(
        new DefaultKafkaProducerFactory<String, Object>(
            new HashMap<>(source.getConfigurationProperties()),
            new StringSerializer(),
            new DelegatingByTypeSerializer(serializers)));
  }
}
