package com.saas.auditcompliance.service;

import com.saas.auditcompliance.dto.eventDto.inbound.OrganizationUnitSyncEvent;

import java.util.UUID;

public interface ReferenceDataSyncService {

    void syncOrganizationUnit(UUID tenantId, OrganizationUnitSyncEvent event);

    void reconcileAll(UUID tenantId);
}