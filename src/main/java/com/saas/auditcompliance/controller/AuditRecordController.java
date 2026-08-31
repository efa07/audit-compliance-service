package com.saas.auditcompliance.controller;

import com.saas.auditcompliance.dto.requestDto.AuditRecordSearchRequest;
import com.saas.auditcompliance.dto.responseDto.AuditRecordResponse;
import com.saas.auditcompliance.service.AuditRecordService;
import com.saas.auditcompliance.utility.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-compliance/audit-records/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Audit Record")
public class AuditRecordController {

    private final AuditRecordService auditRecordService;
    private final SecurityUtil securityUtil;

    @GetMapping("/{id}")
    public ResponseEntity<AuditRecordResponse> getById(@PathVariable UUID tenantId, @PathVariable UUID id) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());
        return ResponseEntity.ok(auditRecordService.getById(resolvedTenantId, id));
    }

    @GetMapping
    public ResponseEntity<Page<AuditRecordResponse>> search(@PathVariable UUID tenantId,
                                                              @RequestParam(required = false) String entityType,
                                                              @RequestParam(required = false) String entityId,
                                                              @RequestParam(required = false) UUID actorId,
                                                              @RequestParam(required = false) LocalDateTime dateFrom,
                                                              @RequestParam(required = false) LocalDateTime dateTo,
                                                              Pageable pageable) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());

        AuditRecordSearchRequest request = new AuditRecordSearchRequest();
        request.setEntityType(entityType);
        request.setEntityId(entityId);
        request.setActorId(actorId);
        request.setDateFrom(dateFrom);
        request.setDateTo(dateTo);

        return ResponseEntity.ok(auditRecordService.search(resolvedTenantId, request, pageable));
    }
}