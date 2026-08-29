package com.saas.auditcompliance.service;

import com.saas.auditcompliance.dto.requestDto.ReviewViolationRequest;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationReviewResponse;

import java.util.UUID;

public interface ComplianceViolationReviewService {

    ComplianceViolationReviewResponse review(UUID tenantId, UUID violationId, UUID reviewerId, ReviewViolationRequest request);

    ComplianceViolationReviewResponse getByViolationId(UUID tenantId, UUID violationId);
}