package com.saas.auditcompliance.model;

import com.saas.auditcompliance.enums.ReviewStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "compliance_violation_reviews",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_review_violation", columnNames = {"violationId"})
    },
    indexes = {
        @Index(name = "idx_review_status", columnList = "tenantId, status")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class ComplianceViolationReview extends Base {

    @Column(nullable = false)
    private UUID violationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewStatus status;

    private UUID reviewedBy;

    private LocalDateTime reviewedAt;

    private String resolutionNotes;
}