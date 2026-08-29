package com.saas.auditcompliance.service;

import java.util.UUID;

public interface IngestionHealthService {

    void recordEventReceived(UUID tenantId, com.saas.auditcompliance.enums.SourceService sourceService, String sourceEventId);

    void checkForGaps();
}