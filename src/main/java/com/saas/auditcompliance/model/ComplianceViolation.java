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

    @Column(nullable = false)
    private UUID auditRecordId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceService sourceService;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViolationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViolationSeverity severity;

    @Column(nullable = false)
    private String ruleViolated;

    @Column(nullable = false)
    private String description;

    private String entityType;

    private String entityId;

    private UUID actorId;

    @Column(nullable = false)
    private LocalDateTime detectedAt;
}