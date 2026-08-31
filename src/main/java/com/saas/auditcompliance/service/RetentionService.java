package com.saas.auditcompliance.service;

import com.saas.auditcompliance.dto.responseDto.RetentionSummaryResponse;

import java.util.UUID;

public interface RetentionService {

    RetentionSummaryResponse getRetentionSummary(UUID tenantId);
}