package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.dto.eventDto.outbound.IngestionGapDetectedEvent;
import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.model.SourceIngestionStatus;
import com.saas.auditcompliance.repository.SourceIngestionStatusRepository;
import com.saas.auditcompliance.service.AuditEventPublisherService;
import com.saas.auditcompliance.service.IngestionHealthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionHealthServiceImpl implements IngestionHealthService {

    private static final String ROUTING_INGESTION_GAP = "audit.ingestion.gap.detected";

    private final SourceIngestionStatusRepository sourceIngestionStatusRepository;
    private final AuditEventPublisherService eventPublisherService;

    @Value("${audit-compliance.ingestion.stale-threshold-hours:24}")
    private long staleThresholdHours;

    @Override
    @Transactional
    public void recordEventReceived(UUID tenantId, SourceService sourceService, String sourceEventId) {
        SourceIngestionStatus status = sourceIngestionStatusRepository
                .findForUpdateByTenantIdAndSourceService(tenantId, sourceService)
                .orElseGet(() -> newStatus(tenantId, sourceService));

        status.setLastEventReceivedAt(LocalDateTime.now());
        status.setLastSourceEventId(sourceEventId);
        status.setEventsReceivedCount(status.getEventsReceivedCount() + 1);

        if (status.isGapSuspected()) {
            log.info("Ingestion resumed for tenant={}, sourceService={} after a suspected gap — clearing flag",
                    tenantId, sourceService);
            status.setGapSuspected(false);
            status.setGapDetectedAt(null);
        }

        sourceIngestionStatusRepository.save(status);
    }

    @Override
    public void checkForGaps() {
        List<SourceService> everySourceService = List.of(SourceService.values());
        LocalDateTime staleThreshold = LocalDateTime.now().minus(Duration.ofHours(staleThresholdHours));

        for (SourceService sourceService : everySourceService) {
            if (sourceService == SourceService.AUDIT_COMPLIANCE || sourceService == SourceService.UNKNOWN) {
                continue;
            }
            checkSourceAcrossAllTenants(sourceService, staleThreshold);
        }
    }

   private void checkSourceAcrossAllTenants(SourceService sourceService, LocalDateTime staleThreshold) {
    List<SourceIngestionStatus> staleStatuses = sourceIngestionStatusRepository
            .findBySourceServiceAndLastEventReceivedAtBeforeAndGapSuspectedFalse(sourceService, staleThreshold);

    for (SourceIngestionStatus status : staleStatuses) {
        flagGap(status);
    }
}

    @Transactional
    protected void flagGap(SourceIngestionStatus status) {
        status.setGapSuspected(true);
        status.setGapDetectedAt(LocalDateTime.now());
        sourceIngestionStatusRepository.save(status);

        IngestionGapDetectedEvent event = new IngestionGapDetectedEvent();
        event.setTenantId(status.getTenantId());
        event.setSourceService(status.getSourceService());
        event.setLastEventReceivedAt(status.getLastEventReceivedAt());
        event.setDetectedAt(status.getGapDetectedAt());

        eventPublisherService.publish(
                status.getTenantId(),
                "audit.ingestion.gap.detected",
                ROUTING_INGESTION_GAP,
                event
        );

        log.warn("Ingestion gap detected: tenant={}, sourceService={}, lastEventReceivedAt={}",
                status.getTenantId(), status.getSourceService(), status.getLastEventReceivedAt());
    }

    private SourceIngestionStatus newStatus(UUID tenantId, SourceService sourceService) {
        SourceIngestionStatus status = new SourceIngestionStatus();
        status.setTenantId(tenantId);
        status.setSourceService(sourceService);
        status.setLastEventReceivedAt(LocalDateTime.now());
        status.setLastSourceEventId("");
        status.setEventsReceivedCount(0);
        status.setGapSuspected(false);
        return status;
    }
}