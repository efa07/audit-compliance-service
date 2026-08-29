package com.saas.auditcompliance.service;

import com.saas.auditcompliance.model.AuditRecord;

public interface ComplianceMonitoringService {

    void evaluate(AuditRecord record);
}