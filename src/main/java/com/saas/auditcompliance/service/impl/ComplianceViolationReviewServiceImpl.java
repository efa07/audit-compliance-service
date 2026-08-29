package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.dto.requestDto.ReviewViolationRequest;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationReviewResponse;
import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.exception.AuditRecordNotFoundException;
import com.saas.auditcompliance.mapper.ComplianceViolationReviewMapper;
import com.saas.auditcompliance.model.ComplianceViolation;
import com.saas.auditcompliance.model.ComplianceViolationReview;
import com.saas.auditcompliance.repository.ComplianceViolationRepository;
import com.saas.auditcompliance.repository.ComplianceViolationReviewRepository;
import com.saas.auditcompliance.service.AuditRecordService;
import com.saas.auditcompliance.service.ComplianceViolationReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceViolationReviewServiceImpl implements ComplianceViolationReviewService {

    private final ComplianceViolationReviewRepository reviewRepository;
    private final ComplianceViolationRepository violationRepository;
    private final AuditRecordService auditRecordService;
    private final ComplianceViolationReviewMapper reviewMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ComplianceViolationReviewResponse review(UUID tenantId, UUID violationId, UUID reviewerId,
                                                      ReviewViolationRequest request) {

        ComplianceViolation violation = violationRepository.findByTenantIdAndId(tenantId, violationId)
                .orElseThrow(() -> new AuditRecordNotFoundException("Compliance violation not found: " + violationId));

        ComplianceViolationReview review = reviewRepository.findByTenantIdAndViolationId(tenantId, violationId)
                .orElseGet(() -> newReview(tenantId, violationId));

        String previousStatus = review.getStatus() != null ? review.getStatus().name() : "NONE";

        review.setStatus(request.getStatus());
        review.setReviewedBy(reviewerId);
        review.setReviewedAt(LocalDateTime.now());
        review.setResolutionNotes(request.getResolutionNotes());

        ComplianceViolationReview saved = reviewRepository.save(review);

        recordReviewAsAuditEvent(tenantId, reviewerId, violation, saved, previousStatus);

        return reviewMapper.toResponse(saved);
    }

    @Override
    public ComplianceViolationReviewResponse getByViolationId(UUID tenantId, UUID violationId) {
        ComplianceViolationReview review = reviewRepository.findByTenantIdAndViolationId(tenantId, violationId)
                .orElseThrow(() -> new AuditRecordNotFoundException(
                        "No review exists yet for violation: " + violationId));
        return reviewMapper.toResponse(review);
    }

    /**
     * Closes the audit-loopback: reviewing a violation is itself an auditable action.
     * Recorded via the same AuditRecordService.ingest(...) path every other event goes
     * through, with sourceService = AUDIT_COMPLIANCE, so this service's own case-management
     * activity ends up in the same hash-chained, immutable trail as everything else it observes.
     */
    private void recordReviewAsAuditEvent(UUID tenantId, UUID reviewerId, ComplianceViolation violation,
                                           ComplianceViolationReview review, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("violationId", violation.getId());
        payload.put("previousStatus", previousStatus);
        payload.put("newStatus", review.getStatus().name());
        payload.put("resolutionNotes", review.getResolutionNotes());

        String rawPayload;
        try {
            rawPayload = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Failed to serialize review audit payload for violation {} — skipping self-audit record " +
                    "(the review itself was still saved successfully)", violation.getId(), e);
            return;
        }

        AuditEventIngestCommand command = new AuditEventIngestCommand(
                tenantId,
                SourceService.AUDIT_COMPLIANCE,
                UUID.randomUUID().toString(),
                "audit.compliance-violation-review.updated",
                LocalDateTime.now(),
                null,
                reviewerId,
                "complianceViolationReview",
                review.getId().toString(),
                rawPayload
        );

        try {
            auditRecordService.ingest(command);
        } catch (Exception e) {
            log.error("Failed to record self-audit event for review {} — the review itself was saved successfully",
                    review.getId(), e);
        }
    }

    private ComplianceViolationReview newReview(UUID tenantId, UUID violationId) {
        ComplianceViolationReview review = new ComplianceViolationReview();
        review.setTenantId(tenantId);
        review.setViolationId(violationId);
        return review;
    }
}