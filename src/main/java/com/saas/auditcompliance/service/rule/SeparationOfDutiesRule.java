package com.saas.auditcompliance.service.rule;

import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;
import com.saas.auditcompliance.model.AuditRecord;
import com.saas.auditcompliance.repository.AuditRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SeparationOfDutiesRule implements ComplianceRule {

    private final AuditRecordRepository auditRecordRepository;

    @Override
    public ComplianceCheckResult evaluate(AuditRecord record) {
        if (record.getEventType() == null || !record.getEventType().endsWith(".approved")) {
            return ComplianceCheckResult.clean();
        }
        if (record.getActorId() == null || record.getEntityType() == null || record.getEntityId() == null) {
            return ComplianceCheckResult.clean();
        }

        Optional<AuditRecord> origination = auditRecordRepository
                .findFirstByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        record.getTenantId(), record.getEntityType(), record.getEntityId());

        if (origination.isEmpty() || origination.get().getActorId() == null) {
            return ComplianceCheckResult.clean();
        }

        if (origination.get().getActorId().equals(record.getActorId())) {
            return new ComplianceCheckResult(
                    true,
                    ViolationType.SEPARATION_OF_DUTIES,
                    ViolationSeverity.HIGH,
                    "SEPARATION_OF_DUTIES: requester == approver",
                    String.format("Actor %s both originated and approved entity %s/%s",
                            record.getActorId(), record.getEntityType(), record.getEntityId())
            );
        }

        return ComplianceCheckResult.clean();
    }
}