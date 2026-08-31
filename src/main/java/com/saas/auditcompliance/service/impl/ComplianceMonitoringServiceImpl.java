package com.saas.auditcompliance.service.impl;

import com.saas.auditcompliance.dto.requestDto.ComplianceViolationSearchRequest;
import com.saas.auditcompliance.dto.responseDto.ComplianceViolationResponse;
import com.saas.auditcompliance.exception.AuditRecordNotFoundException;
import com.saas.auditcompliance.mapper.ComplianceViolationMapper;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.model.ComplianceViolation;
import com.saas.auditcompliance.repository.ComplianceViolationRepository;
import com.saas.auditcompliance.service.AuditEventPublisherService;
import com.saas.auditcompliance.service.ComplianceMonitoringService;
import com.saas.auditcompliance.service.rule.ComplianceCheckResult;
import com.saas.auditcompliance.service.rule.ComplianceRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceMonitoringServiceImpl implements ComplianceMonitoringService {

    private static final String ROUTING_VIOLATION_DETECTED = "audit.compliance.violation.detected";

    private final List<ComplianceRule> rules;
    private final ComplianceViolationRepository complianceViolationRepository;
    private final AuditEventPublisherService eventPublisherService;
    private final ComplianceViolationMapper complianceViolationMapper;

    @Override
    public void evaluate(AuditRecord record) {
        for (ComplianceRule rule : rules) {
            ComplianceCheckResult result;
            try {
                result = rule.evaluate(record);
            } catch (Exception e) {
                log.error("Compliance rule {} threw an exception evaluating audit record {} — skipping this rule",
                        rule.getClass().getSimpleName(), record.getId(), e);
                continue;
            }

            if (result.violated()) {
                recordViolationSafely(record, result);
            }
        }
    }

    @Override
    public ComplianceViolationResponse getById(UUID tenantId, UUID id) {
        ComplianceViolation violation = complianceViolationRepository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new AuditRecordNotFoundException("Compliance violation not found: " + id));
        return complianceViolationMapper.toResponse(violation);
    }

    @Override
    public Page<ComplianceViolationResponse> search(UUID tenantId, ComplianceViolationSearchRequest request,
                                                      Pageable pageable) {
        Page<ComplianceViolation> violations;

        if (request.getEntityType() != null && request.getEntityId() != null) {
            violations = complianceViolationRepository.findByTenantIdAndEntityTypeAndEntityId(
                    tenantId, request.getEntityType(), request.getEntityId(), pageable);
        } else if (request.getType() != null && request.getSeverity() != null) {
            violations = complianceViolationRepository.findByTenantIdAndTypeAndSeverity(
                    tenantId, request.getType(), request.getSeverity(), pageable);
        } else if (request.getSourceService() != null) {
            violations = complianceViolationRepository.findByTenantIdAndSourceService(
                    tenantId, request.getSourceService(), pageable);
        } else if (request.getActorId() != null) {
            violations = complianceViolationRepository.findByTenantIdAndActorId(
                    tenantId, request.getActorId(), pageable);
        } else {
            throw new IllegalArgumentException(
                    "At least one filter (entityType+entityId, type+severity, sourceService, or actorId) is required");
        }

        return violations.map(complianceViolationMapper::toResponse);
    }

    private void recordViolationSafely(AuditRecord record, ComplianceCheckResult result) {
        try {
            recordViolation(record, result);
        } catch (Exception e) {
            log.error("FAILED TO RECORD COMPLIANCE VIOLATION — a real violation was detected but could not be " +
                    "persisted or published. This is a compliance-monitoring gap requiring manual follow-up. " +
                    "auditRecordId={}, ruleViolated={}, tenantId={}",
                    record.getId(), result.ruleViolated(), record.getTenantId(), e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void recordViolation(AuditRecord record, ComplianceCheckResult result) {
        ComplianceViolation violation = new ComplianceViolation();
        violation.setTenantId(record.getTenantId());
        violation.setAuditRecordId(record.getId());
        violation.setSourceService(record.getSourceService());
        violation.setType(result.type());
        violation.setSeverity(result.severity());
        violation.setRuleViolated(result.ruleViolated());
        violation.setDescription(result.description());
        violation.setEntityType(record.getEntityType());
        violation.setEntityId(record.getEntityId());
        violation.setActorId(record.getActorId());
        violation.setDetectedAt(LocalDateTime.now());

        ComplianceViolation saved = complianceViolationRepository.save(violation);

        eventPublisherService.publish(
                record.getTenantId(),
                "audit.compliance.violation.detected",
                ROUTING_VIOLATION_DETECTED,
                complianceViolationMapper.toDetectedEvent(saved),
                record.getCorrelationId()
        );
    }
}