package com.saas.auditcompliance.model;

import com.saas.auditcompliance.enums.SourceService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "audit_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_audit_record_source_event",
            columnNames = {"sourceService", "sourceEventId"}
        )
    },
    indexes = {
        @Index(name = "idx_audit_chain_lookup", columnList = "tenantId, sourceService, createdAt"),
        @Index(name = "idx_audit_entity_trail", columnList = "tenantId, entityType, entityId"),
        @Index(name = "idx_audit_actor_activity", columnList = "tenantId, actorId, createdAt"),
        @Index(name = "idx_audit_retention", columnList = "retainUntil")
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class AuditRecord extends Base {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceService sourceService;

    @Column(nullable = false)
    private String sourceEventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    private String correlationId;

    private UUID actorId;

    private String entityType;

    private String entityId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, length = 64)
    private String recordHash;

    @Column(length = 64)
    private String previousRecordHash;

    @Column(nullable = false)
    private LocalDateTime retainUntil;
}