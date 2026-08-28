package com.saas.auditcompliance.service;

import java.util.UUID;

public interface AuditEventPublisherService {

    void publish(UUID tenantId, String eventType, String routingKey, Object payload);

    void publish(UUID tenantId, String eventType, String routingKey, Object payload, String correlationId);
}