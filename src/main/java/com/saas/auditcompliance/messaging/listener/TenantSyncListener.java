package com.saas.auditcompliance.messaging.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.saas.auditcompliance.model.refcache.RefTenant;
import com.saas.auditcompliance.repository.refcache.RefTenantRepository;
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
public class TenantSyncListener {

    private final RefTenantRepository refTenantRepository;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${audit-compliance.rabbitmq.queues.tenant-create:create-tenant-queue}")
    public void onTenantCreated(String messageBody, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(messageBody, channel, deliveryTag, true);
    }

    @RabbitListener(queues = "${audit-compliance.rabbitmq.queues.tenant-delete:delete-tenant-queue}")
    public void onTenantDeleted(String messageBody, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        handle(messageBody, channel, deliveryTag, false);
    }

    private void handle(String messageBody, Channel channel, long deliveryTag, boolean isCreate) throws IOException {
        try {
            JsonNode node = objectMapper.readTree(messageBody);

            // NOTE: organization-service's actual tenant-event payload shape is unconfirmed —
            // we only have the queue names, not a documented schema. This tries the most
            // likely field names defensively. Confirm the real shape with that team and
            // tighten this once known, rather than trusting this guess long-term.
            UUID externalId = UUID.fromString(firstNonMissing(node, "tenantId", "id"));
            String name = firstNonMissing(node, "name", "tenantName");

            RefTenant tenant = refTenantRepository.findByExternalId(externalId).orElseGet(RefTenant::new);
            tenant.setExternalId(externalId);
            tenant.setName(name != null ? name : tenant.getName());
            tenant.setActive(isCreate);
            tenant.setSyncedAt(LocalDateTime.now());

            refTenantRepository.save(tenant);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process tenant {} event, sending to dead-letter queue: {}",
                    isCreate ? "create" : "delete", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private String firstNonMissing(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (!value.isMissingNode() && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }
}