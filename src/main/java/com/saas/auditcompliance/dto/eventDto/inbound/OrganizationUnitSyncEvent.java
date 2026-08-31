package com.saas.auditcompliance.dto.eventDto.inbound;

import lombok.Data;

import java.util.UUID;

@Data
public class OrganizationUnitSyncEvent {
    private UUID externalId;
    private String code;
    private String name;
    private UUID parentExternalId;
    private boolean active;
}