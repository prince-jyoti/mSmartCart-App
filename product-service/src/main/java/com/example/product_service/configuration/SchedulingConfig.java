package com.example.product_service.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Runs @Scheduled jobs (ProcessedEventCleanup).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
