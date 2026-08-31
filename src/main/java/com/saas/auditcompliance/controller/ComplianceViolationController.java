package com.saas.auditcompliance.controller;

import com.saas.auditcompliance.dto.requestDto.ComplianceViolationSearchRequest;
import com.saas.auditcompliance.dto.requestDto.ReviewViolationRequest;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationResponse;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationReviewResponse;
import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import com.saas.auditcompliance.service.ComplianceMonitoringService;
import com.saas.auditcompliance.service.ComplianceViolationReviewService;
import com.saas.auditcompliance.utility.PermissionUtil;
import com.saas.auditcompliance.utility.SecurityUtil;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-compliance/compliance-violations/{tenantId}")
@RequiredArgsConstructor
@Tag(name = "Compliance Violation")
public class ComplianceViolationController {

    private final ComplianceMonitoringService complianceMonitoringService;
    private final ComplianceViolationReviewService reviewService;
    private final PermissionUtil permissionUtil;
    private final SecurityUtil securityUtil;

    @GetMapping("/{id}")
    public ResponseEntity<ComplianceViolationResponse> getById(@PathVariable UUID tenantId, @PathVariable UUID id) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());
        return ResponseEntity.ok(complianceMonitoringService.getById(resolvedTenantId, id));
    }

    @GetMapping
    public ResponseEntity<Page<ComplianceViolationResponse>> search(@PathVariable UUID tenantId,
                                                                      @RequestParam(required = false) SourceService sourceService,
                                                                      @RequestParam(required = false) ViolationType type,
                                                                      @RequestParam(required = false) ViolationSeverity severity,
                                                                      @RequestParam(required = false) String entityType,
                                                                      @RequestParam(required = false) String entityId,
                                                                      @RequestParam(required = false) UUID actorId,
                                                                      Pageable pageable) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());

        ComplianceViolationSearchRequest request = new ComplianceViolationSearchRequest();
        request.setSourceService(sourceService);
        request.setType(type);
        request.setSeverity(severity);
        request.setEntityType(entityType);
        request.setEntityId(entityId);
        request.setActorId(actorId);

        return ResponseEntity.ok(complianceMonitoringService.search(resolvedTenantId, request, pageable));
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<ComplianceViolationReviewResponse> getReview(@PathVariable UUID tenantId, @PathVariable UUID id) {
        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());
        return ResponseEntity.ok(reviewService.getByViolationId(resolvedTenantId, id));
    }

    @PostMapping("/{id}/review")
    public ResponseEntity<ComplianceViolationReviewResponse> review(@PathVariable UUID tenantId, @PathVariable UUID id,
                                                                      @Valid @RequestBody ReviewViolationRequest request) {
        if (!permissionUtil.hasPermission(tenantId, "compliance-violation-review")) {
            throw new AccessDeniedException("You do not have permission to review compliance violations");
        }

        UUID resolvedTenantId = UUID.fromString(securityUtil.getTenantId());
        UUID reviewerId = UUID.fromString(securityUtil.getUserId());

        return ResponseEntity.ok(reviewService.review(resolvedTenantId, id, reviewerId, request));
    }
}