package com.saas.auditcompliance.exception;

/**
 * Exception thrown when a requested resource is not found.
 * Used as the parent exception for resource-specific not-found exceptions
 * (e.g., AuditRecordNotFoundException, ComplianceViolationNotFoundException).
 */
public class ResourceNotFoundException extends RuntimeException {
    
    public ResourceNotFoundException(String message) {
        super(message);
    }
    
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
