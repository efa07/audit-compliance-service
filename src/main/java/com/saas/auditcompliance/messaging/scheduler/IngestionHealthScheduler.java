package com.saas.auditcompliance.messaging.scheduler;

import com.saas.auditcompliance.service.IngestionHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionHealthScheduler {

    private final IngestionHealthService ingestionHealthService;

    @Scheduled(fixedDelayString = "${audit-compliance.ingestion.check-interval-ms:3600000}")
    public void checkForGaps() {
        log.debug("Running scheduled ingestion-gap sweep");
        try {
            ingestionHealthService.checkForGaps();
        } catch (Exception e) {
            log.error("Ingestion-gap sweep failed unexpectedly: {}", e.getMessage(), e);
        }
    }
}