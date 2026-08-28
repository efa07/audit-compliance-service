package com.saas.auditcompliance.service;

import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.dto.requestDto.AuditRecordSearchRequest;
import com.saas.auditcompliance.dto.responseDto.AuditRecordResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditRecordService {

    void ingest(AuditEventIngestCommand command);

    AuditRecordResponse getById(UUID tenantId, UUID id);

    Page<AuditRecordResponse> search(UUID tenantId, AuditRecordSearchRequest request, Pageable pageable);

    Page<AuditRecordResponse> findByEventTypes(UUID tenantId, List<String> eventTypes,
                                                LocalDateTime from, LocalDateTime to, Pageable pageable);
}