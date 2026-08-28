package com.saas.auditcompliance.messaging.outbox;

import com.saas.auditcompliance.model.OutboxEvent;
import com.saas.auditcompliance.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayScheduler {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRY_COUNT = 5;

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${audit-compliance.rabbitmq.exchange}")
    private String exchangeName;

    @Scheduled(fixedDelayString = "${audit-compliance.outbox.relay-interval-ms:5000}")
    @Transactional
    public void relayPendingEvents() {
        Pageable batch = PageRequest.of(0, BATCH_SIZE);
        List<OutboxEvent> pendingEvents = outboxEventRepository.findUnpublishedOrderByCreatedAtAsc(batch);

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            relayEvent(event);
        }
    }

    private void relayEvent(OutboxEvent event) {
        if (event.getRetryCount() >= MAX_RETRY_COUNT) {
            log.error("Outbox event {} exceeded max retry count ({}). Skipping — requires manual investigation. lastError={}",
                    event.getId(), MAX_RETRY_COUNT, event.getLastError());
            return;
        }

        try {
            Message message = MessageBuilder
                    .withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                    .setMessageId(event.getId().toString())
                    .setCorrelationId(event.getCorrelationId())
                    .build();

            rabbitTemplate.send(exchangeName, event.getRoutingKey(), message);

            event.setPublished(true);
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            outboxEventRepository.save(event);

        } catch (Exception e) {
            log.warn("Failed to relay outbox event {} (attempt {}): {}",
                    event.getId(), event.getRetryCount() + 1, e.getMessage());

            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastError(truncate(e.getMessage(), 1000));
            outboxEventRepository.save(event);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}