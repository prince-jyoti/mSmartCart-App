package com.example.cart_service.service;

import com.example.cart_service.repository.ProcessedEventRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ProcessedEventCleanupTest {
    @Test
    void deletesEverythingOlderThanTheRetention() {
        ProcessedEventRepo repo = mock(ProcessedEventRepo.class);
        ProcessedEventCleanup cleanup = new ProcessedEventCleanup();
        ReflectionTestUtils.setField(cleanup, "repo", repo);
        ReflectionTestUtils.setField(cleanup, "retention", Duration.ofDays(30));

        LocalDateTime before = LocalDateTime.now();
        cleanup.deleteOld();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deleteProcessedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(30).minusSeconds(5), before.minusDays(30).plusSeconds(5));
    }
}
