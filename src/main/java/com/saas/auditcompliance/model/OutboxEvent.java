package com.saas.auditcompliance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "outbox_events",
    indexes = {
        @Index(name = "idx_outbox_published", columnList = "published, createdAt")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class OutboxEvent extends Base {

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private String routingKey;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private String correlationId;

    @Column(nullable = false)
    private boolean published = false;

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private int retryCount = 0;

    private String lastError;
}