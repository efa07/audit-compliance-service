package com.saas.auditcompliance.model;

import com.saas.auditcompliance.enums.SourceService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "source_ingestion_status",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_ingestion_status_source", columnNames = {"tenantId", "sourceService"})
    }
)
@Data
@EqualsAndHashCode(callSuper = true)
public class SourceIngestionStatus extends Base {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceService sourceService;

    @Column(nullable = false)
    private LocalDateTime lastEventReceivedAt;

    @Column(nullable = false)
    private String lastSourceEventId;

    @Column(nullable = false)
    private long eventsReceivedCount;

    @Column(nullable = false)
    private boolean gapSuspected;

    private LocalDateTime gapDetectedAt;
}