package com.saas.auditcompliance.messaging.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.saas.auditcompliance.dto.common.AuditEventIngestCommand;
import com.saas.auditcompliance.enums.SourceService;
import com.saas.auditcompliance.service.AuditRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventListener {

    private final AuditRecordService auditRecordService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = {
        "${audit-compliance.rabbitmq.queues.finance-admin-audit}",
        "${audit-compliance.rabbitmq.queues.budget-management-audit}",
        "${audit-compliance.rabbitmq.queues.general-ledger-audit}",
        "${audit-compliance.rabbitmq.queues.cash-banking-audit}",
        "${audit-compliance.rabbitmq.queues.accounts-payable-audit}",
        "${audit-compliance.rabbitmq.queues.accounts-receivable-audit}",
        "${audit-compliance.rabbitmq.queues.employee-finance-audit}",
        "${audit-compliance.rabbitmq.queues.asset-finance-audit}",
        "${audit-compliance.rabbitmq.queues.financial-reporting-audit}"
    })

    public void onAuditableEvent(String messageBody,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
    try {
        JsonNode envelope = objectMapper.readTree(messageBody);

        String eventType = envelope.path("eventType").asText(null);
        if (eventType == null) {
            log.error("Received event with no eventType — cannot route or classify. Rejecting without requeue: {}", messageBody);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        String occurredAtRaw = envelope.path("occurredAt").asText(null);
        if (occurredAtRaw == null) {
            log.error("Received event with no occurredAt — cannot ingest without a real timestamp " +
                    "(would silently defeat ingestion-gap detection). Rejecting without requeue. eventType={}, body={}",
                    eventType, messageBody);
            channel.basicNack(deliveryTag, false, false);
            return;
        }

        UUID tenantId = UUID.fromString(envelope.path("tenantId").asText());
        String sourceEventId = envelope.path("eventId").asText();
        String correlationId = envelope.path("correlationId").asText(null);
        LocalDateTime occurredAt = LocalDateTime.parse(occurredAtRaw);
        JsonNode payloadNode = envelope.path("payload");

        SourceService sourceService = resolveSourceService(eventType);

        AuditEventIngestCommand command = new AuditEventIngestCommand(
                tenantId,
                sourceService,
                sourceEventId,
                eventType,
                occurredAt,
                correlationId,
                extractActorId(payloadNode),
                extractEntityType(eventType),
                extractEntityId(payloadNode),
                payloadNode.toString()
        );

        auditRecordService.ingest(command);

        channel.basicAck(deliveryTag, false);

    } catch (Exception e) {
        log.error("Failed to ingest auditable event, sending to dead-letter queue: {}", e.getMessage(), e);
        channel.basicNack(deliveryTag, false, false);
    }
}

    private SourceService resolveSourceService(String eventType) {
        String prefix = eventType.contains(".") ? eventType.substring(0, eventType.indexOf('.')) : eventType;
        return switch (prefix) {
            case "finance-admin" -> SourceService.FINANCE_ADMINISTRATION;
            case "budget" -> SourceService.BUDGET_MANAGEMENT;
            case "general-ledger" -> SourceService.GENERAL_LEDGER;
            case "cash-banking" -> SourceService.CASH_BANKING;
            case "accounts-payable" -> SourceService.ACCOUNTS_PAYABLE;
            case "accounts-receivable" -> SourceService.ACCOUNTS_RECEIVABLE;
            case "employee-finance" -> SourceService.EMPLOYEE_FINANCE;
            case "asset-finance" -> SourceService.ASSET_INVENTORY_FINANCE;
            case "financial-reporting" -> SourceService.FINANCIAL_REPORTING;
            default -> {
                log.warn("Unrecognized event type prefix '{}' — recording with source service left unmapped", prefix);
                yield SourceService.AUDIT_COMPLIANCE; // fallback bucket, not a real claim of self-origin
            }
        };
    }

    private LocalDateTime parseOccurredAt(String raw) {
        return raw != null ? LocalDateTime.parse(raw) : LocalDateTime.now();
    }

    private UUID extractActorId(JsonNode payload) {
        for (String field : new String[]{"actorId", "requestedBy", "approvedBy", "createdBy", "userId"}) {
            JsonNode node = payload.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                try {
                    return UUID.fromString(node.asText());
                } catch (IllegalArgumentException ignored) {
                    // not a UUID-shaped value, skip
                }
            }
        }
        return null;
    }

    private String extractEntityId(JsonNode payload) {
        for (String field : new String[]{"id", "entityId"}) {
            JsonNode node = payload.path(field);
            if (!node.isMissingNode() && !node.isNull()) {
                return node.asText();
            }
        }
        return null;
    }

    private String extractEntityType(String eventType) {
        // e.g. "budget.plan.approved" -> "plan"
        String[] parts = eventType.split("\\.");
        return parts.length >= 2 ? parts[1] : null;
    }
}