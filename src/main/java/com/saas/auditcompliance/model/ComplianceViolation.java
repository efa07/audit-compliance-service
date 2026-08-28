package com.saas.auditcompliance.model;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "compliance_violations",
    indexes = {
        @Index(name = "idx_violation_source", columnList = "tenantId, sourceService, detectedAt"),
        @Index(name = "idx_violation_type_severity", columnList = "tenantId, type, severity"),
        @Index(name = "idx_violation_entity", columnList = "tenantId, entityType, entityId"),
        @Index(name = "idx_violation_actor", columnList = "tenantId, actorId")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class ComplianceViolation extends Base {

    @Column(nullable = false, updatable = false)
    private UUID auditRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SourceService sourceService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ViolationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ViolationSeverity severity;

    @Column(nullable = false, updatable = false)
    private String ruleViolated;

    @Column(nullable = false, updatable = false)
    private String description;

    @Column(updatable = false)
    private String entityType;

    @Column(updatable = false)
    private String entityId;

    @Column(updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime detectedAt;
}