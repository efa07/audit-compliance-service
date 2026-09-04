CREATE TABLE source_ingestion_status (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    source_service VARCHAR(255) NOT NULL,
    last_event_received_at DATETIME(6) NOT NULL,
    last_source_event_id VARCHAR(255) NOT NULL,
    events_received_count BIGINT NOT NULL,
    gap_suspected BIT(1) NOT NULL,
    gap_detected_at DATETIME(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_ingestion_status_source UNIQUE (tenant_id, source_service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
