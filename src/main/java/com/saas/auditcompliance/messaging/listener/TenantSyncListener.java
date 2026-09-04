package com.saas.auditcompliance.messaging.listener;

import com.saas.auditcompliance.model.refcache.RefTenant;
import com.saas.auditcompliance.repository.refcache.RefTenantRepository;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSyncListener {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final RefTenantRepository refTenantRepository;

    @RabbitListener(queues = "${audit-compliance.rabbitmq.queues.tenant-create:create-tenant-queue}")
    public void onTenantCreated(TenantEventDto event, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            if (event.getTenantId() == null) {
                log.error("Received create-tenant event with no tenantId — rejecting without requeue: {}", event);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            RefTenant tenant = refTenantRepository.findByExternalId(event.getTenantId())
                    .orElseGet(RefTenant::new);
            tenant.setExternalId(event.getTenantId());
            tenant.setName(event.getAbbreviatedName());
            tenant.setActive(true);
            tenant.setSyncedAt(LocalDateTime.now());

            refTenantRepository.save(tenant);
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process tenant creation event, sending to dead-letter queue: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = "${audit-compliance.rabbitmq.queues.tenant-delete:delete-tenant-queue}")
    public void onTenantDeleted(String rawTenantId, Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            if (rawTenantId == null || !UUID_PATTERN.matcher(rawTenantId.trim()).matches()) {
                log.error("Received delete-tenant event that is not a valid UUID string — rejecting without requeue: '{}'",
                        rawTenantId);
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            UUID tenantId = UUID.fromString(rawTenantId.trim());
            refTenantRepository.findByExternalId(tenantId).ifPresentOrElse(
                    tenant -> {
                        tenant.setActive(false);
                        tenant.setSyncedAt(LocalDateTime.now());
                        refTenantRepository.save(tenant);
                    },
                    () -> log.warn("Received delete-tenant event for a tenant not present in local cache: {}", tenantId)
            );

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Failed to process tenant deletion event, sending to dead-letter queue: {}", e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}