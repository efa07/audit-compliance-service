package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.dto.requestDto.AuditRecordSearchRequest;
import com.saas.auditcompliance.dto.responseDto.AuditRecordResponse;
import com.saas.auditcompliance.exception.AuditRecordNotFoundException;
import com.saas.auditcompliance.mapper.AuditRecordMapper;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import com.saas.auditcompliance.utility.AuditHashChainUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.saas.auditcompliance.service.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRecordServiceImpl implements AuditRecordService {

    private static final int RETENTION_YEARS = 10;

    private final AuditRecordRepository auditRecordRepository;
    private final AuditHashChainUtil hashChainUtil;
    private final AuditRecordMapper auditRecordMapper;
    private final ComplianceMonitoringService complianceMonitoringService;
    private final IngestionHealthService ingestionHealthService;

    @Override
    @Transactional
    public void ingest(AuditEventIngestCommand command) {

        boolean alreadyIngested = auditRecordRepository
                .findByTenantIdAndSourceServiceAndSourceEventId(
                        command.getTenantId(), command.getSourceService(), command.getSourceEventId())
                .isPresent();

        if (alreadyIngested) {
            log.info("Audit event already ingested, skipping (idempotent): sourceService={}, sourceEventId={}",
                    command.getSourceService(), command.getSourceEventId());
            return;
        }

        AuditHashChainUtil.ChainLink chainLink = hashChainUtil.computeNext(command);

        AuditRecord record = new AuditRecord();
        record.setTenantId(command.getTenantId());
        record.setSourceService(command.getSourceService());
        record.setSourceEventId(command.getSourceEventId());
        record.setEventType(command.getEventType());
        record.setOccurredAt(command.getOccurredAt());
        record.setCorrelationId(command.getCorrelationId());
        record.setActorId(command.getActorId());
        record.setEntityType(command.getEntityType());
        record.setEntityId(command.getEntityId());
        record.setPayload(command.getRawPayload());
        record.setRecordHash(chainLink.recordHash());
        record.setPreviousRecordHash(chainLink.previousRecordHash());
        record.setRetainUntil(LocalDateTime.now().plusYears(RETENTION_YEARS));

        AuditRecord saved = auditRecordRepository.save(record);

        recordIngestionHealthSafely(command);
        evaluateComplianceSafely(saved);
    }

    /**
     * Same isolation reasoning as evaluateComplianceSafely below — recording that an
     * event was successfully ingested must never be able to roll back the ingestion
     * itself, and a failure here must not block compliance evaluation from still running.
     */
    private void recordIngestionHealthSafely(AuditEventIngestCommand command) {
        try {
            ingestionHealthService.recordEventReceived(
                    command.getTenantId(), command.getSourceService(), command.getSourceEventId());
        } catch (Exception e) {
            log.error("Failed to update ingestion health status for sourceService={}, sourceEventId={} — " +
                    "the audit record itself was saved successfully; only health tracking failed.",
                    command.getSourceService(), command.getSourceEventId(), e);
        }
    }

    /**
     * Compliance evaluation must never be able to roll back or block the underlying
     * audit record save — recording that something happened must always succeed,
     * even if judging it fails. Any exception here is logged and swallowed, not rethrown.
     */
    private void evaluateComplianceSafely(AuditRecord record) {
        try {
            complianceMonitoringService.evaluate(record);
        } catch (Exception e) {
            log.error("Compliance evaluation failed for audit record {} (sourceService={}, sourceEventId={}) — " +
                    "the audit record itself was saved successfully; only rule evaluation failed.",
                    record.getId(), record.getSourceService(), record.getSourceEventId(), e);
        }
    }

    @Override
    public AuditRecordResponse getById(UUID tenantId, UUID id) {
        AuditRecord record = auditRecordRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new AuditRecordNotFoundException("Audit record not found: " + id));
        return auditRecordMapper.toResponse(record);
    }

    @Override
    public Page<AuditRecordResponse> search(UUID tenantId, AuditRecordSearchRequest request, Pageable pageable) {
        Page<AuditRecord> records;

        if (request.getEntityType() != null && request.getEntityId() != null) {
            records = auditRecordRepository.findByTenantIdAndEntityTypeAndEntityId(
                    tenantId, request.getEntityType(), request.getEntityId(), pageable);
        } else if (request.getActorId() != null) {
            records = auditRecordRepository.findByTenantIdAndActorId(tenantId, request.getActorId(), pageable);
        } else if (request.getDateFrom() != null && request.getDateTo() != null) {
            records = auditRecordRepository.findByTenantIdAndCreatedAtBetween(
                    tenantId, request.getDateFrom(), request.getDateTo(), pageable);
        } else {
            throw new IllegalArgumentException(
                    "At least one filter (entityType+entityId, actorId, or a date range) is required");
        }

        return records.map(auditRecordMapper::toResponse);
    }

    @Override
    public Page<AuditRecordResponse> findByEventTypes(UUID tenantId, List<String> eventTypes,
                                                        LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return auditRecordRepository
                .findByTenantIdAndEventTypeInAndCreatedAtBetween(tenantId, eventTypes, from, to, pageable)
                .map(auditRecordMapper::toResponse);
    }

}