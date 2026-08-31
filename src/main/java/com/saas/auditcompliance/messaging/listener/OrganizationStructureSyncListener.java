package com.saas.auditcompliance.messaging.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.saas.auditcompliance.dto.eventDto.inbound.OrganizationUnitSyncEvent;
import com.saas.auditcompliance.service.ReferenceDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrganizationStructureSyncListener {

    private final ReferenceDataSyncService referenceDataSyncService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${audit-compliance.rabbitmq.queues.org-structure-sync}")
    public void onOrganizationUnitEvent(String messageBody,
                                         Channel channel,
                                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            JsonNode envelope = objectMapper.readTree(messageBody);
            UUID tenantId = UUID.fromString(envelope.path("tenantId").asText());
            JsonNode payloadNode = envelope.path("payload");

            OrganizationUnitSyncEvent event = objectMapper.treeToValue(payloadNode, OrganizationUnitSyncEvent.class);
            referenceDataSyncService.syncOrganizationUnit(tenantId, event);

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process organization structure sync event, sending to dead-letter queue: {}",
                    e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}