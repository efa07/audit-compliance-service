package com.saas.auditcompliance.repository;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import com.saas.auditcompliance.model.ComplianceViolation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.UUID;

public interface ComplianceViolationRepository extends Repository<ComplianceViolation, UUID> {

    ComplianceViolation save(ComplianceViolation violation);

    Optional<ComplianceViolation> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<ComplianceViolation> findByTenantIdAndSourceService(
            UUID tenantId, SourceService sourceService, Pageable pageable);

    Page<ComplianceViolation> findByTenantIdAndTypeAndSeverity(
            UUID tenantId, ViolationType type, ViolationSeverity severity, Pageable pageable);

    Page<ComplianceViolation> findByTenantIdAndEntityTypeAndEntityId(
            UUID tenantId, String entityType, String entityId, Pageable pageable);

    Page<ComplianceViolation> findByTenantIdAndActorId(UUID tenantId, UUID actorId, Pageable pageable);

    Optional<ComplianceViolation> findByTenantIdAndAuditRecordId(UUID tenantId, UUID auditRecordId);
}