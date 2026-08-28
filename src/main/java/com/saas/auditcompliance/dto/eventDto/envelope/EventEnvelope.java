package com.saas.auditcompliance.dto.eventDto.envelope;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope<T> {

    private UUID eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private UUID tenantId;
    private String correlationId;
    private int version;
    private T payload;
}