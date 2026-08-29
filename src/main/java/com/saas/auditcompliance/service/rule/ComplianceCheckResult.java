package com.saas.auditcompliance.service.rule;

import com.saas.auditcompliance.enums.ViolationSeverity;
import com.saas.auditcompliance.enums.ViolationType;

public record ComplianceCheckResult(
        boolean violated,
        ViolationType type,
        ViolationSeverity severity,
        String ruleViolated,
        String description
) {
    public static ComplianceCheckResult clean() {
        return new ComplianceCheckResult(false, null, null, null, null);
    }
}