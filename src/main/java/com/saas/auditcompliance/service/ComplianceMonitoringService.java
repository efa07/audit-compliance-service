package com.saas.auditcompliance.service;

import com.saas.auditcompliance.dto.requestDto.ComplianceViolationSearchRequest;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationResponse;
import com.saas.auditcompliance.model.AuditRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ComplianceMonitoringService {

    void evaluate(AuditRecord record);

    ComplianceViolationResponse getById(UUID tenantId, UUID id);

    Page<ComplianceViolationResponse> search(UUID tenantId, ComplianceViolationSearchRequest request, Pageable pageable);
}