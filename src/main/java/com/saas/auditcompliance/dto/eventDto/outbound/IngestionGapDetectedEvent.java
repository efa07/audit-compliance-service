package com.saas.auditcompliance.dto.eventDto.outbound;

import com.saas.auditcompliance.enums.SourceService;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class IngestionGapDetectedEvent {

    private UUID tenantId;
    private SourceService sourceService;
    private LocalDateTime lastEventReceivedAt;
    private LocalDateTime detectedAt;
}