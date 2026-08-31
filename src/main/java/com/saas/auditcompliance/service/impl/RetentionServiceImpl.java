package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.dto.responseDto.RetentionSummaryResponse;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import com.saas.auditcompliance.service.RetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetentionServiceImpl implements RetentionService {

    private final AuditRecordRepository auditRecordRepository;

    @Override
    public RetentionSummaryResponse getRetentionSummary(UUID tenantId) {
        long eligibleCount = auditRecordRepository
                .countByTenantIdAndRetainUntilBefore(tenantId, LocalDateTime.now());

        String note = eligibleCount > 0
                ? "These records have passed their 10-year retention period. Physical archival/deletion is a " +
                  "deliberately separate, privileged process — not implemented in this service's normal write path."
                : "No records currently past their retention period.";

        return new RetentionSummaryResponse(eligibleCount, note);
    }
}