package com.example.order_service.service;

import com.example.order_service.repository.OutboxRepo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxCleanupTest {
    @Test
    void deletesEverythingOlderThanTheRetention() {
        OutboxRepo repo = mock(OutboxRepo.class);
        OutboxCleanup cleanup = new OutboxCleanup();
        ReflectionTestUtils.setField(cleanup, "repo", repo);
        ReflectionTestUtils.setField(cleanup, "retention", Duration.ofDays(7));

        LocalDateTime before = LocalDateTime.now();
        cleanup.deleteOld();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repo).deletePublishedBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBetween(before.minusDays(7).minusSeconds(5), before.minusDays(7).plusSeconds(5));
    }
}
