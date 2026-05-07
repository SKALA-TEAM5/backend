package com.skala.backend.aiagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.ai-agent")
public class AiAgentProperties {

	private String baseUrl;
	private Duration connectTimeout = Duration.ofSeconds(3);
	private Duration readTimeout = Duration.ofSeconds(60);
	private String runPath = "/agent-runs";

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getReadTimeout() {
		return readTimeout;
	}

	public void setReadTimeout(Duration readTimeout) {
		this.readTimeout = readTimeout;
	}

	public String getRunPath() {
		return runPath;
	}

	public void setRunPath(String runPath) {
		this.runPath = runPath;
	}
}
