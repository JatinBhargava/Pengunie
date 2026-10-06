package com.pengunie.jobs;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.jobs")
public record JobProperties(Duration pollInterval, int batchSize, Duration lease,
		@DefaultValue("true") boolean workerEnabled) {
}
