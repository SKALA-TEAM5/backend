package com.skala.backend.agent.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class AgentDispatchMetrics {

	private final MeterRegistry meterRegistry;
	private final Map<String, AtomicInteger> inProgress = new ConcurrentHashMap<>();

	public AgentDispatchMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	public DispatchSample start(String operation) {
		inProgress(operation).incrementAndGet();
		return new DispatchSample(operation, Timer.start(meterRegistry));
	}

	public void success(DispatchSample sample) {
		finish(sample, "success");
	}

	public void failure(DispatchSample sample) {
		finish(sample, "fail");
	}

	public void recordTodoRefreshFailure() {
		Counter.builder("backend.agent.todo.refresh")
				.description("Number of agent TODO refresh failures.")
				.tag("result", "fail")
				.register(meterRegistry)
				.increment();
	}

	private void finish(DispatchSample sample, String result) {
		Counter.builder("backend.agent.dispatch")
				.description("Number of FastAPI agent dispatches from the backend.")
				.tags("operation", sample.operation(), "result", result)
				.register(meterRegistry)
				.increment();
		sample.timer().stop(Timer.builder("backend.agent.dispatch.duration")
				.description("FastAPI agent dispatch duration.")
				.tag("operation", sample.operation())
				.register(meterRegistry));
		inProgress(sample.operation()).decrementAndGet();
	}

	private AtomicInteger inProgress(String operation) {
		return inProgress.computeIfAbsent(operation, key -> {
			AtomicInteger value = new AtomicInteger();
			Gauge.builder("backend.agent.dispatch.in.progress", value, AtomicInteger::get)
					.description("Number of FastAPI agent dispatches currently in progress.")
					.tag("operation", key)
					.register(meterRegistry);
			return value;
		});
	}

	public record DispatchSample(String operation, Timer.Sample timer) {
	}
}
