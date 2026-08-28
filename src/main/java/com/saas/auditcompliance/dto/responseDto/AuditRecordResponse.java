package com.saas.auditcompliance.dto.responseDto;

import com.saas.auditcompliance.enums.SourceService;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class AuditRecordResponse extends BaseResponse {

    private SourceService sourceService;
    private String sourceEventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private String correlationId;
    private UUID actorId;
    private String entityType;
    private String entityId;
    private String payload;
    private String recordHash;
    private String previousRecordHash;
    private LocalDateTime retainUntil;
}