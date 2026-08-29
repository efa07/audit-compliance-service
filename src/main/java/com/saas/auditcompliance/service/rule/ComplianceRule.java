package com.saas.auditcompliance.service.rule;

import com.saas.auditcompliance.model.AuditRecord;

public interface ComplianceRule {

    ComplianceCheckResult evaluate(AuditRecord record);
}