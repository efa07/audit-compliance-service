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

        String canonicalContent = buildCanonicalContent(command);
        AuditHashChainUtil.ChainLink chainLink = hashChainUtil.computeNext(
                command.getTenantId(), command.getSourceService(), canonicalContent);

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

        auditRecordRepository.save(record);
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

    private String buildCanonicalContent(AuditEventIngestCommand command) {
        return String.join("|",
                command.getTenantId().toString(),
                command.getSourceService().name(),
                command.getSourceEventId(),
                command.getEventType(),
                command.getOccurredAt().toString(),
                command.getRawPayload()
        );
    }
}