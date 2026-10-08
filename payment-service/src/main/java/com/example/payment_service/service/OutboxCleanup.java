package com.example.payment_service.service;

import com.example.payment_service.repository.OutboxRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

// Published outbox rows are only history once RabbitMQ has confirmed them; without this they
// pile up forever. Unpublished rows are never touched.
@Slf4j
@Component
public class OutboxCleanup {
    @Autowired
    private OutboxRepo repo;

    @Value("${outbox.cleanup.retention:P7D}")
    private Duration retention;

    @Scheduled(fixedDelayString = "${outbox.cleanup.interval:PT1H}", initialDelayString = "PT1M")
    @Transactional
    public void deleteOld() {
        int deleted = repo.deletePublishedBefore(LocalDateTime.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} published outbox events older than {}", deleted, retention);
        }
    }
}
