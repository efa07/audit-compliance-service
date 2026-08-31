package com.saas.auditcompliance.dto.requestDto;

import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import lombok.Data;

import java.util.UUID;

@Data
public class ComplianceViolationSearchRequest {

    private SourceService sourceService;
    private ViolationType type;
    private ViolationSeverity severity;
    private String entityType;
    private String entityId;
    private UUID actorId;
}