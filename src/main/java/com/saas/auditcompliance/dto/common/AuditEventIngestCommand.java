package com.saas.auditcompliance.dto.common;

import com.saas.auditcompliance.enums.SourceService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventIngestCommand {

    private UUID tenantId;
    private SourceService sourceService;
    private String sourceEventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String correlationId;
    private UUID actorId;
    private String entityType;
    private String entityId;
    private String rawPayload;
}