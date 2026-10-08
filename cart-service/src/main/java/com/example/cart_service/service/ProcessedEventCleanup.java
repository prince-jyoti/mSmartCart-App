package com.example.cart_service.service;

import com.example.cart_service.repository.ProcessedEventRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

// These rows are what make a redelivered message harmless (see the order event listener), so
// they are kept much longer than any redelivery or manual DLQ replay could take. Don't replay
// dead-lettered messages older than the retention: they would be applied a second time.
@Slf4j
@Component
public class ProcessedEventCleanup {
    @Autowired
    private ProcessedEventRepo repo;

    @Value("${processed-events.cleanup.retention:P30D}")
    private Duration retention;

    @Scheduled(fixedDelayString = "${processed-events.cleanup.interval:PT1H}", initialDelayString = "PT1M")
    @Transactional
    public void deleteOld() {
        int deleted = repo.deleteProcessedBefore(LocalDateTime.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} processed event ids older than {}", deleted, retention);
        }
    }
}
