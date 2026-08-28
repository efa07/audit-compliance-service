package com.saas.auditcompliance.exception;

public class AuditRecordNotFoundException extends ResourceNotFoundException {
    public AuditRecordNotFoundException(String message) {
        super(message);
    }
}