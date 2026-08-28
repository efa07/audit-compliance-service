package com.saas.auditcompliance.dto.requestDto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AuditRecordSearchRequest {

    private String entityType;
    private String entityId;
    private UUID actorId;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}