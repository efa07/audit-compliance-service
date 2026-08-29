package com.saas.auditcompliance.dto.eventDto.outbound;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ComplianceViolationDetectedEvent {

    private UUID violationId;
    private UUID auditRecordId;
    private SourceService sourceService;
    private ViolationType type;
    private ViolationSeverity severity;
    private String ruleViolated;
    private String entityType;
    private String entityId;
    private UUID actorId;
    private LocalDateTime detectedAt;
}