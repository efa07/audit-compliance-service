CREATE TABLE outbox_events (
    id BINARY(16) NOT NULL,
    tenant_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6),
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    event_type VARCHAR(255) NOT NULL,
    routing_key VARCHAR(255) NOT NULL,
    payload TEXT NOT NULL,
    correlation_id VARCHAR(255) NOT NULL,
    published BIT(1) NOT NULL,
    published_at DATETIME(6),
    retry_count INT NOT NULL,
    last_error VARCHAR(255),
    PRIMARY KEY (id),
    INDEX idx_outbox_published (published, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
