package com.saas.auditcompliance.repository;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.model.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuditRecordRepository extends Repository<AuditRecord, UUID> {

    AuditRecord save(AuditRecord record);

    Optional<AuditRecord> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<AuditRecord> findByTenantIdAndSourceServiceAndSourceEventId(
            UUID tenantId, SourceService sourceService, String sourceEventId);

    Optional<AuditRecord> findTopByTenantIdAndSourceServiceOrderByCreatedAtDesc(
            UUID tenantId, SourceService sourceService);

    Page<AuditRecord> findByTenantIdAndEntityTypeAndEntityId(
            UUID tenantId, String entityType, String entityId, Pageable pageable);

    Page<AuditRecord> findByTenantIdAndActorId(UUID tenantId, UUID actorId, Pageable pageable);

    Page<AuditRecord> findByTenantIdAndCreatedAtBetween(
            UUID tenantId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<AuditRecord> findByTenantIdAndEventTypeInAndCreatedAtBetween(
            UUID tenantId, List<String> eventTypes, LocalDateTime from, LocalDateTime to, Pageable pageable);
}