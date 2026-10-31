package dev.tejesh.leaderboard.config;

import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

@Configuration
public class ObservabilityConfig {

  /** The 200 ms poller would otherwise produce five empty traces a second. */
  @Bean
  public ObservationPredicate skipScheduledTasks() {
    return (name, context) -> !name.equals("tasks.scheduled.execution");
  }

  /** Prometheus scrapes and health probes aren't traffic. */
  @Bean
  public ObservationPredicate skipActuatorRequests() {
    return (name, context) ->
        !(context instanceof ServerRequestObservationContext request)
            || !request.getCarrier().getRequestURI().startsWith("/actuator");
  }
}
