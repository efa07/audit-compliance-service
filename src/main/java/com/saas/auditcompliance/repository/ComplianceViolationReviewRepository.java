package com.saas.auditcompliance.repository;

import com.saas.auditcompliance.enums.ReviewStatus;
import com.saas.auditcompliance.model.ComplianceViolationReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ComplianceViolationReviewRepository extends JpaRepository<ComplianceViolationReview, UUID> {

    Optional<ComplianceViolationReview> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ComplianceViolationReview> findByTenantIdAndViolationId(UUID tenantId, UUID violationId);

    Page<ComplianceViolationReview> findByTenantIdAndStatus(UUID tenantId, ReviewStatus status, Pageable pageable);

    Page<ComplianceViolationReview> findByTenantIdAndReviewedBy(UUID tenantId, UUID reviewedBy, Pageable pageable);
}