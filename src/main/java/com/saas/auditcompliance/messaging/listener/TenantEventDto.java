package com.saas.auditcompliance.messaging.listener;

import lombok.Data;

import java.util.UUID;

@Data
public class TenantEventDto {
    private UUID tenantId;
    private String abbreviatedName;
}