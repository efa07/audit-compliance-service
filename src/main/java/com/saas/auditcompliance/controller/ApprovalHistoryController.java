package com.saas.auditcompliance.controller;

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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-compliance/approval-history/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Approval History")
public class ApprovalHistoryController {


    private static final List<String> APPROVAL_EVENT_TYPES = List.of(
            "budget.plan.approved", "budget.plan.rejected",
            "budget.transfer.approved",
            "budget.revision.created"
    );

    private final AuditRecordService auditRecordService;
    private final SecurityUtil securityUtil;

    @GetMapping
    public ResponseEntity<Page<AuditRecordResponse>> list(@PathVariable UUID tenantId,
                                                            @RequestParam LocalDateTime dateFrom,
                                                            @RequestParam LocalDateTime dateTo,
                                                            Pageable pageable) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());
        return ResponseEntity.ok(
                auditRecordService.findByEventTypes(resolvedTenantId, APPROVAL_EVENT_TYPES, dateFrom, dateTo, pageable));
    }
}