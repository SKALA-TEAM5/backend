package com.skala.backend.agent.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDispatchMetricsTest {

	@Test
	void recordsDispatchResultDurationInProgressAndTodoFailure() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		AgentDispatchMetrics metrics = new AgentDispatchMetrics(registry);

		AgentDispatchMetrics.DispatchSample success = metrics.start("validate");
		assertThat(registry.get("backend.agent.dispatch.in.progress")
				.tag("operation", "validate")
				.gauge()
				.value()).isEqualTo(1);
		metrics.success(success);

		AgentDispatchMetrics.DispatchSample failure = metrics.start("validate");
		metrics.failure(failure);
		metrics.recordTodoRefreshFailure();

		assertThat(registry.get("backend.agent.dispatch")
				.tags("operation", "validate", "result", "success")
				.counter()
				.count()).isEqualTo(1);
		assertThat(registry.get("backend.agent.dispatch")
				.tags("operation", "validate", "result", "fail")
				.counter()
				.count()).isEqualTo(1);
		assertThat(registry.get("backend.agent.dispatch.duration")
				.tag("operation", "validate")
				.timer()
				.count()).isEqualTo(2);
		assertThat(registry.get("backend.agent.dispatch.in.progress")
				.tag("operation", "validate")
				.gauge()
				.value()).isZero();
		assertThat(registry.get("backend.agent.todo.refresh")
				.tag("result", "fail")
				.counter()
				.count()).isEqualTo(1);
	}
}
